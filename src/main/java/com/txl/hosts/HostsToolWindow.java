package com.txl.hosts;

import com.intellij.icons.AllIcons;
import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HostsToolWindow {
    private JPanel HostsToolWindowContent;

    private JButton addButton;
    private JButton saveButton;
    private JButton applyToHostButton;
    private JButton deleteButton;
    private JButton refreshButton;

    private JList<String> itemList;
    private DefaultListModel<String> listModel;
    private JScrollPane listScrollPane;

    private JTextPane textPane;
    private JScrollPane textScrollPane;

    private static final String CONFIG_FILE = System.getProperty("user.home") + "/hosts_config"; // 配置文件路径
    private static final String SYSTEM_HOSTS_FILE = getSystemHostsFilePath(); // 系统 hosts 文件路径
    private Map<String, HostConfig> hostConfigs = new HashMap<>();

    public HostsToolWindow() {
        HostsToolWindowContent = new JPanel(new BorderLayout());
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        topPanel.setBorder(BorderFactory.createEmptyBorder());

        buildAddButton();
        topPanel.add(addButton);

        buildDeleteButton();
        topPanel.add(deleteButton);

        buildSaveButton();
        topPanel.add(saveButton);

        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(5, 20));
        topPanel.add(separator);

        buildApplyToHostButton();
        topPanel.add(applyToHostButton);

        buildRefreshButton();
        topPanel.add(refreshButton);

        HostsToolWindowContent.add(topPanel, BorderLayout.NORTH);

        buildListScrollPane();

        buildTextScrollPane();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScrollPane, textScrollPane);
        splitPane.setDividerSize(5);
        splitPane.setDividerLocation(200);
        splitPane.setUI(new InvisibleSplitPaneUI());
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        HostsToolWindowContent.add(splitPane, BorderLayout.CENTER);

        loadHostConfigs();
    }

    private void buildListScrollPane() {
        listModel = new DefaultListModel<>();
        itemList = new JList<>(listModel);
        itemList.setBackground(new Color(0, 0, 0, 0)); // 设置透明背景
        itemList.setOpaque(false);
        listScrollPane = new JScrollPane(itemList);
        listScrollPane.setBorder(new EmptyBorder(5, 5, 5, 0));
        addItemListListener();
    }

    private void buildTextScrollPane() {
        textPane = new JTextPane();
        textPane.setBorder(BorderFactory.createEmptyBorder());
        textPane.setBackground(new Color(0x313335));
        textPane.setOpaque(true);

        StyledDocument doc = textPane.getStyledDocument();
        Style defaultStyle = doc.addStyle("default", null);
        Style ipStyle = doc.addStyle("ip", null);
        StyleConstants.setForeground(ipStyle, Color.BLUE); // Change color for IP addresses

        textScrollPane = new JScrollPane(textPane);
        textScrollPane.setBorder(BorderFactory.createEmptyBorder());
    }

    private void buildRefreshButton() {
        refreshButton = new JButton();
        refreshButton.setContentAreaFilled(false);

        refreshButton.setIcon(IconManager.getInstance().getIcon("actions/refresh.svg", AllIcons.class));
        refreshButton.setPreferredSize(new Dimension(25, 25));
        refreshButton.setMargin(new Insets(1, 1, 1, 1));
        refreshButton.setHorizontalAlignment(SwingConstants.CENTER);
        refreshButton.setVerticalAlignment(SwingConstants.CENTER);
        refreshButton.setBorder(BorderFactory.createEmptyBorder());
        refreshButton.setToolTipText("同步最新Host内容"); // 设置中文描述
        refreshButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                refreshButton.setContentAreaFilled(true); // 鼠标悬浮时填充背景
            }

            @Override
            public void mouseExited(MouseEvent e) {
                refreshButton.setContentAreaFilled(false); // 鼠标离开时去除背景填充
            }
        });
        refreshButton.addActionListener(e -> {
            String hostsFileContentStr = getHostsContent().toString();
            // 更新active=true的HostConfig
            hostConfigs.forEach((name, config) -> {
                if (config.isActive()) {
                    // 更新配置的内容为hosts文件中的内容
                    config.setContent(hostsFileContentStr);
                    saveHostConfigs();
                    setSelectedItem(name);
                    JOptionPane.showMessageDialog(
                            HostsToolWindowContent,
                            "已从hosts文件刷新 " + name + " 的内容",
                            "刷新成功",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                }
            });
        });
    }

    private void buildApplyToHostButton() {
        applyToHostButton = new JButton();
        applyToHostButton.setContentAreaFilled(false);
        applyToHostButton.setIcon(IconManager.getInstance().getIcon("actions/setDefault.svg", AllIcons.class));
        applyToHostButton.setPreferredSize(new Dimension(25, 25));
        applyToHostButton.setMargin(new Insets(1, 1, 1, 1));
        applyToHostButton.setHorizontalAlignment(SwingConstants.CENTER);
        applyToHostButton.setVerticalAlignment(SwingConstants.CENTER);
        applyToHostButton.setBorder(BorderFactory.createEmptyBorder());
        applyToHostButton.setToolTipText("应用到Host");
        applyToHostButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                applyToHostButton.setContentAreaFilled(true); // 鼠标悬浮时填充背景
            }

            @Override
            public void mouseExited(MouseEvent e) {
                applyToHostButton.setContentAreaFilled(false); // 鼠标离开时去除背景填充
            }
        });
        applyToHostButton.addActionListener(e -> {
            String selectedHost = itemList.getSelectedValue(); // 获取当前选中的主机配置
            if (selectedHost == null) {
                JOptionPane.showMessageDialog(
                        HostsToolWindowContent,
                        "没有选中任何主机配置",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            HostConfig selectedConfig = hostConfigs.get(selectedHost);
            if (selectedConfig != null) {
                String contentToApply = selectedConfig.getContent();
                try {
                    // 创建临时 BAT 文件
                    Path tempBatFile = Files.createTempFile("update_hosts", ".bat");
                    tempBatFile.toFile().deleteOnExit(); // 确保临时文件在退出时删除

                    // 创建临时内容文件
                    Path tempContentFile = Files.createTempFile("hosts_content", ".txt");
                    tempContentFile.toFile().deleteOnExit(); // 确保内容文件在退出时删除

                    // 将内容写入临时文件
                    Files.write(tempContentFile, contentToApply.getBytes(), StandardOpenOption.WRITE);

                    // 创建 BAT 文件内容（读取临时内容文件并更新 hosts 文件）
                    String batContent = "@echo off\n" +
                            "set \"TARGET_FILE=%windir%\\System32\\drivers\\etc\\hosts\"\n" +
                            "echo Deleting read-only attribute...\n" +
                            "attrib -r \"%TARGET_FILE%\"\n" +
                            "echo Updating hosts file with content from: " + tempContentFile + "\n" +
                            "type \"" + tempContentFile + "\" > \"%TARGET_FILE%\"\n" +
                            "echo Adding read-only attribute...\n" +
                            "attrib +r \"%TARGET_FILE%\"\n" +
                            "echo File updated successfully!\n";

                    // 写入 BAT 文件
                    Files.write(tempBatFile, batContent.getBytes(), StandardOpenOption.WRITE);

                    // 准备 PowerShell 命令以隐藏窗口运行 BAT 文件
                    String batFilePath = tempBatFile.toAbsolutePath().toString();
                    String powershellCommand = "powershell -Command \"Start-Process cmd.exe -ArgumentList '/c \"" +
                            batFilePath + "\"' -WindowStyle Hidden -Verb RunAs\"";

                    // 执行 PowerShell 命令
                    ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", powershellCommand);
                    pb.redirectErrorStream(true);  // 捕获标准错误和标准输出
                    Process process = pb.start();

                    // 读取 PowerShell 输出
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);  // 输出 PowerShell 执行结果
                        }
                    }

                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        JOptionPane.showMessageDialog(
                                HostsToolWindowContent,
                                "更新 Hosts 文件时发生错误，错误代码: " + exitCode,
                                "错误",
                                JOptionPane.ERROR_MESSAGE
                        );
                    } else {
                        hostConfigs.forEach((name, config) -> {
                            config.setActive(false);
                            if (selectedHost.equals(name)) {
                                config.setActive(true);
                            }
                        });
                        saveHostConfigs();
                        setSelectedItem(selectedHost);
                        JOptionPane.showMessageDialog(
                                HostsToolWindowContent,
                                "Hosts 文件更新成功",
                                "成功",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }

                } catch (IOException | InterruptedException i) {
                    JOptionPane.showMessageDialog(
                            HostsToolWindowContent,
                            "更新 Hosts 文件时发生错误: " + i.getMessage(),
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
    }

    private void buildSaveButton() {
        saveButton = new JButton();
        saveButton.setContentAreaFilled(false);
        saveButton.setIcon(IconManager.getInstance().getIcon("actions/menu-saveall.svg", AllIcons.class));
        saveButton.setPreferredSize(new Dimension(25, 25));
        saveButton.setMargin(new Insets(1, 1, 1, 1));
        saveButton.setHorizontalAlignment(SwingConstants.CENTER);
        saveButton.setVerticalAlignment(SwingConstants.CENTER);
        saveButton.setBorder(BorderFactory.createEmptyBorder());
        saveButton.setToolTipText("保存配置");
        // 添加鼠标监听器实现悬浮时的背景填充效果
        saveButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                saveButton.setContentAreaFilled(true); // 鼠标悬浮时填充背景
            }

            @Override
            public void mouseExited(MouseEvent e) {
                saveButton.setContentAreaFilled(false); // 鼠标离开时去除背景填充
            }
        });
        saveButton.addActionListener(e -> {
            String selectedHost = itemList.getSelectedValue(); // 获取当前选中的主机配置
            if (selectedHost == null) {
                JOptionPane.showMessageDialog(
                        HostsToolWindowContent,
                        "没有选中任何主机配置",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // 获取右侧编辑框的内容
            String updatedContent = textPane.getText();

            // 更新当前HostConfig的内容
            HostConfig selectedConfig = hostConfigs.get(selectedHost);
            if (selectedConfig != null) {
                selectedConfig.setContent(updatedContent);
                saveHostConfigs(); // 将更新后的配置保存到文件中

                JOptionPane.showMessageDialog(
                        HostsToolWindowContent,
                        "主机配置已成功保存",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });
    }

    private void buildDeleteButton() {
        deleteButton = new JButton(); // 删除按钮
        deleteButton.setContentAreaFilled(false);
        deleteButton.setIcon(IconManager.getInstance().getIcon("expui/general/remove.svg", AllIcons.class));
        deleteButton.setPreferredSize(new Dimension(25, 25));
        deleteButton.setMargin(new Insets(1, 1, 1, 1));
        deleteButton.setHorizontalAlignment(SwingConstants.CENTER);
        deleteButton.setVerticalAlignment(SwingConstants.CENTER);
        deleteButton.setBorder(BorderFactory.createEmptyBorder());
        deleteButton.setToolTipText("删除Host配置");
        // 添加鼠标监听器实现悬浮时的背景填充效果
        deleteButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                deleteButton.setContentAreaFilled(true); // 鼠标悬浮时填充背景
            }

            @Override
            public void mouseExited(MouseEvent e) {
                deleteButton.setContentAreaFilled(false); // 鼠标离开时去除背景填充
            }
        });
        deleteButton.addActionListener(e -> {
            String selectedHost = itemList.getSelectedValue();
            if (selectedHost == null) {
                JOptionPane.showMessageDialog(HostsToolWindowContent, "没有选中任何主机配置", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (hostConfigs.get(selectedHost).isActive()) {
                JOptionPane.showMessageDialog(HostsToolWindowContent, "正在使用的配置无法删除", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }


            int confirm = JOptionPane.showConfirmDialog(HostsToolWindowContent, "确认删除配置: " + selectedHost + " ?", "确认删除", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                hostConfigs.remove(selectedHost); // 从hostConfigs中移除
                listModel.removeElement(selectedHost); // 从列表中移除
                saveHostConfigs(); // 更新保存后的文件
                hostConfigs.forEach((name, config) -> {
                    if (config.isActive()) {
                        displayHostContent(name); // 显示当前激活的配置
                    }
                });
                JOptionPane.showMessageDialog(HostsToolWindowContent, "配置已成功删除", "成功", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    private void buildAddButton() {
        addButton = new JButton();
        addButton.setContentAreaFilled(false);
        addButton.setIcon(IconManager.getInstance().getIcon("expui/general/add.svg", AllIcons.class));
        addButton.setPreferredSize(new Dimension(25, 25));
        addButton.setMargin(new Insets(1, 1, 1, 1));
        addButton.setHorizontalAlignment(SwingConstants.CENTER);
        addButton.setVerticalAlignment(SwingConstants.CENTER);
        addButton.setBorder(BorderFactory.createEmptyBorder());
        addButton.setToolTipText("添加新的HOSTS配置"); // 设置中文描述
        addButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                addButton.setContentAreaFilled(true); // 鼠标悬浮时填充背景
            }

            @Override
            public void mouseExited(MouseEvent e) {
                addButton.setContentAreaFilled(false); // 鼠标离开时去除背景填充
            }
        });
        addButton.addActionListener(e -> {
            String newHostName = JOptionPane.showInputDialog(
                    HostsToolWindowContent,
                    "请输入新的配置名称:",
                    "添加配置",
                    JOptionPane.PLAIN_MESSAGE
            );

            // 如果输入为空或者用户取消操作
            if (newHostName == null || newHostName.trim().isEmpty()) {
                return; // 取消操作，直接返回
            }

            // 校验名称是否重复
            if (hostConfigs.containsKey(newHostName)) {
                JOptionPane.showMessageDialog(
                        HostsToolWindowContent,
                        "配置名称已存在，请输入其他名称",
                        "名称重复",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // 保存新配置并更新
            String defaultContent = "#你可以在此输入新的主机配置内容"; // 你可以自定义默认内容
            HostConfig newHostConfig = new HostConfig(newHostName, defaultContent, false);
            hostConfigs.put(newHostName, newHostConfig);
            listModel.addElement(newHostName); // 更新左侧列表
            saveHostConfigs(); // 保存到文件中

            JOptionPane.showMessageDialog(
                    HostsToolWindowContent,
                    "配置已成功添加",
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });
    }

    private void addItemListListener() {
        itemList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedItem = itemList.getSelectedValue();
                if (selectedItem != null) {
                    displayHostContent(selectedItem);
                }
            }
        });
        itemList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 检查是否为双击
                if (e.getClickCount() == 2) {
                    int index = itemList.locationToIndex(e.getPoint()); // 获取被双击的项目索引
                    if (index >= 0) {
                        String oldName = itemList.getModel().getElementAt(index); // 获取当前选中的项目
                        String newName = JOptionPane.showInputDialog(
                                HostsToolWindowContent,
                                "请输入新的名称:",
                                oldName
                        );

                        // 如果输入为空或者用户取消操作
                        if (newName == null || newName.trim().isEmpty()) {
                            return; // 取消操作，直接返回
                        }

                        // 校验名称是否重复
                        if (hostConfigs.containsKey(newName)) {
                            JOptionPane.showMessageDialog(
                                    HostsToolWindowContent,
                                    "配置名称已存在，请输入其他名称",
                                    "名称重复",
                                    JOptionPane.ERROR_MESSAGE
                            );
                            return;
                        }
                        // 校验名称是否为默认
                        if ("System Default".equals(oldName)) {
                            JOptionPane.showMessageDialog(
                                    HostsToolWindowContent,
                                    "默认配置无法修改",
                                    "无法修改",
                                    JOptionPane.ERROR_MESSAGE
                            );
                            return;
                        }

                        // 更新 itemList 中的名称
                        DefaultListModel<String> model = (DefaultListModel<String>) itemList.getModel();
                        model.set(index, newName);

                        // 获取旧的配置并删除旧的名称
                        HostConfig oldConfig = hostConfigs.remove(oldName);
                        if (oldConfig != null) {
                            // 更新配置名称并放入新的条目
                            oldConfig.setName(newName);
                            hostConfigs.put(newName, oldConfig);

                            // 保存更新到 JSON 文件
                            saveHostConfigs();

                            JOptionPane.showMessageDialog(
                                    HostsToolWindowContent,
                                    "配置名称已更新",
                                    "成功",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        }
                    }
                }
            }
        });
    }

    // 确保 System Default 始终在第一位的方法
    private void ensureSystemDefaultAtTop() {
        DefaultListModel<String> model = (DefaultListModel<String>) itemList.getModel();

        if (model.contains("System Default")) {
            model.removeElement("System Default"); // 删除并重新添加到第一个位置
        }

        model.add(0, "System Default"); // 添加到列表的顶部
    }

    public JPanel getContent() {
        return HostsToolWindowContent;
    }


    private void loadHostConfigs() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            file = createDefaultConfigs();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            System.out.println("***************" + jsonString);

            JSONObject jsonObject = new JSONObject(jsonString.toString());
            JSONArray hostsArray = jsonObject.getJSONArray("hosts");

            for (int i = 0; i < hostsArray.length(); i++) {
                JSONObject hostObject = hostsArray.getJSONObject(i);
                String name = hostObject.getString("name");
                String content = hostObject.getString("content");
                boolean isActive = hostObject.getBoolean("isActive");
                hostConfigs.put(name, new HostConfig(name, content, isActive));
                listModel.addElement(name);
            }

            // 默认选中当前使用的host
            for (Map.Entry<String, HostConfig> entry : hostConfigs.entrySet()) {
                if (entry.getValue().isActive()) {
                    setSelectedItem(entry.getKey());
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setSelectedItem(String item) {
        itemList.setSelectedValue(item, true);
        String selectedItem = itemList.getSelectedValue();
        // 查找当前激活的配置名称
        String activeHostName = null;
        for (Map.Entry<String, HostConfig> entry : hostConfigs.entrySet()) {
            if (entry.getValue().isActive()) {
                activeHostName = entry.getKey();
                break;
            }
        }
        // 更新 itemList 的渲染器
        itemList.setCellRenderer(new HostListCellRenderer(activeHostName));
        if (selectedItem != null) {
            displayHostContent(selectedItem);
        }
    }

    private File createDefaultConfigs() {
        File configFile = new File(CONFIG_FILE);

        JSONObject jsonObject = new JSONObject();
        JSONArray hostsArray = new JSONArray();

        StringBuilder content = getHostsContent();
        JSONObject hostObject = new JSONObject();
        hostObject.put("name", "system default");
        hostObject.put("content", content.toString());
        hostObject.put("isActive", true);

        hostsArray.put(hostObject);
        jsonObject.put("hosts", hostsArray);


        // Use try-with-resources to ensure resources are closed properly
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8))) {
            writer.write(jsonObject.toString(2)); // Pretty print with an indent of 2
        } catch (IOException e) {
            e.printStackTrace(); // Print stack trace for debugging
            throw new RuntimeException("Error writing to file", e);
        }
        System.out.println("Default configurations created successfully at: " + configFile.getAbsolutePath());
        return configFile;

    }

    @NotNull
    private static StringBuilder getHostsContent() {
        File systemHostsFile = new File(SYSTEM_HOSTS_FILE);

        if (!systemHostsFile.exists()) {
            System.err.println("System hosts file does not exist: " + SYSTEM_HOSTS_FILE);
            return null;
        }
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(systemHostsFile), StandardCharsets.UTF_8));
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            System.err.println("Failed to create default configs: " + e.getMessage());
            e.printStackTrace();
        }
        return content;
    }

    private void saveHostConfigs() {
        JSONObject jsonObject = new JSONObject();
        JSONArray hostsArray = new JSONArray();

        for (HostConfig config : hostConfigs.values()) {
            JSONObject hostObject = new JSONObject();
            hostObject.put("name", config.getName());
            hostObject.put("content", config.getContent());
            hostObject.put("isActive", config.isActive());
            hostsArray.put(hostObject);
        }

        jsonObject.put("hosts", hostsArray);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), "UTF-8"))) {
            writer.write(jsonObject.toString(2)); // Pretty print with an indent of 2
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void displayHostContent(String host) {
        HostConfig config = hostConfigs.get(host);
        if (config != null) {
            String content = config.getContent();
            textPane.setText(content);
        } else {
            textPane.setText("No content available for: " + host);
        }
    }


    private static String getSystemHostsFilePath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "C:\\Windows\\System32\\drivers\\etc\\hosts";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
            return "/etc/hosts";
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + os);
        }
    }

    private void applySyntaxHighlighting() {
        StyledDocument doc = textPane.getStyledDocument();
        Style ipStyle = doc.getStyle("ip");

        // Remove existing styles
        doc.setCharacterAttributes(0, doc.getLength(), doc.getStyle("default"), true);

        // Highlight IP addresses
        String text = textPane.getText();
        Pattern ipPattern = Pattern.compile(
                "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"); // Regular expression for IP addresses
        Matcher matcher = ipPattern.matcher(text);
        while (matcher.find()) {
            doc.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), ipStyle, false);
        }
    }

    // 将资源文件提取到临时目录
    private static Path extractResource(String resourcePath) {
        try (InputStream is = HostsToolWindow.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            Path tempFile = Files.createTempFile("script-", ".vbs");
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit(); // 清理临时文件
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract resource", e);
        }
    }
}

