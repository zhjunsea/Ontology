package com.ocean.installer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本体开发环境安装器（Swing UI）。
 * 客户点按钮选择「软件安装目录」与「开发工作目录」，点「开始安装」后调用 {@link InstallService} 执行安装。
 */
public final class OntologyInstaller {

    private final JFrame frame = new JFrame("本体开发环境安装器 (OntologyInstaller)");
    private final JTextField installDirField = new JTextField(38);
    private final JTextField workDirField = new JTextField(38);
    private final JTextArea logArea = new JTextArea(20, 82);
    private final JButton installButton = new JButton("开始安装");

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new OntologyInstaller().show());
    }

    private void show() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 6, 12));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);

        gc.gridx = 0; gc.gridy = 0; gc.anchor = GridBagConstraints.WEST;
        form.add(new JLabel("软件安装目录："), gc);
        gc.gridx = 1; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(installDirField, gc);
        gc.gridx = 2; gc.weightx = 0; gc.fill = GridBagConstraints.NONE;
        JButton chooseInstall = new JButton("浏览...");
        chooseInstall.addActionListener(e -> chooseDir(installDirField));
        form.add(chooseInstall, gc);

        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0; gc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("开发工作目录："), gc);
        gc.gridx = 1; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(workDirField, gc);
        gc.gridx = 2; gc.weightx = 0; gc.fill = GridBagConstraints.NONE;
        JButton chooseWork = new JButton("浏览...");
        chooseWork.addActionListener(e -> chooseDir(workDirField));
        form.add(chooseWork, gc);

        gc.gridx = 1; gc.gridy = 2; gc.weightx = 0; gc.anchor = GridBagConstraints.WEST;
        form.add(installButton, gc);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(new EmptyBorder(0, 12, 12, 12));

        installButton.addActionListener(e -> onInstall());

        frame.setLayout(new BorderLayout());
        frame.add(form, BorderLayout.NORTH);
        frame.add(scroll, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void chooseDir(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择目录");
        String cur = field.getText().trim();
        if (!cur.isEmpty()) {
            chooser.setCurrentDirectory(new File(cur));
        }
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onInstall() {
        String i = installDirField.getText().trim();
        String w = workDirField.getText().trim();
        if (i.isEmpty() || w.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请先选择「软件安装目录」与「开发工作目录」。",
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final Path installDir = Paths.get(i);
        final Path workDir = Paths.get(w);
        if (installDir.equals(workDir)) {
            JOptionPane.showMessageDialog(frame, "「软件安装目录」与「开发工作目录」不能相同。",
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        installButton.setEnabled(false);
        logArea.setText("");
        appendLog("开始安装 ...");

        Thread t = new Thread(() -> {
            try {
                InstallService.install(installDir, workDir, this::appendLogAsync);
                appendLogAsync("\n[完成] 安装成功。");
                SwingUtilities.invokeLater(() -> {
                    installButton.setEnabled(true);
                    JOptionPane.showMessageDialog(frame,
                            "安装完成！\n工程目录: " + workDir.resolve(InstallService.PROJECT_DIR)
                                    + "\n双击其中的 start-env.cmd 即可一键准备并启动环境。",
                            "完成", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                appendLogAsync("\n[失败] " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    installButton.setEnabled(true);
                    JOptionPane.showMessageDialog(frame, "安装失败: " + ex.getMessage(),
                            "错误", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "installer");
        t.setDaemon(true);
        t.start();
    }

    private void appendLogAsync(String msg) {
        SwingUtilities.invokeLater(() -> appendLog(msg));
    }

    private void appendLog(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
