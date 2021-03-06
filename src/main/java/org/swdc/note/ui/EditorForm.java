package org.swdc.note.ui;

import com.hg.xdoc.XDoc;
import com.hg.xdoc.XDocEditor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.swdc.note.config.BCrypt;
import org.swdc.note.config.UIConfig;
import org.swdc.note.entity.ClipsArtle;
import org.swdc.note.entity.ClipsContent;
import org.swdc.note.entity.DailyArtle;
import org.swdc.note.entity.GlobalType;
import org.swdc.note.service.ClipsService;
import org.swdc.note.service.DailyService;
import org.swdc.note.ui.listener.DataRefreshEvent;
import org.swdc.note.ui.start.PromptTextField;
import org.swdc.note.ui.start.SCenterPane;
import org.swdc.note.ui.start.SWestPane;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;

/**
 * 编辑内容的窗口
 */
@Component
public class EditorForm extends JFrame {

    @Autowired
    private ClipsSaveDialog clipsSaveDialog;

    @Autowired
    private DailysSaveDialog dailysSaveDialog;

    @Autowired
    private ClipsService clipsService;

    @Autowired
    private DailyService dailyService;

    @Autowired
    private ApplicationContext context;

    private XDocEditor editor = new XDocEditor();

    private JPanel contentPane = new JPanel();
    private JToolBar toolBar = new JToolBar();
    private JLabel lblTitle = new JLabel("标题：");
    private PromptTextField titleField = new PromptTextField();
    private JButton btnSave = new JButton();

    /**
     * 当前编辑的id
     */
    private Long currId;
    /**
     * 当前编辑的全局类型
     */
    private GlobalType currType;

    public EditorForm() throws NoSuchFieldException, IllegalAccessException {
        this.setContentPane(contentPane);
        titleField.setPrompt("在这里输入标题");
        contentPane.setLayout(new BorderLayout());
        contentPane.add(editor, BorderLayout.CENTER);
        contentPane.add(toolBar, BorderLayout.NORTH);
        toolBar.add(lblTitle);
        toolBar.add(titleField);
        toolBar.addSeparator(new Dimension(8, toolBar.getHeight()));
        toolBar.add(btnSave);
        toolBar.addSeparator(new Dimension(8, this.getHeight()));
        toolBar.setFloatable(false);
    }

    @Autowired
    public void setConfig(UIConfig config) throws Exception {
        this.setSize(config.getSubWindowWidth(), config.getSubWindowHeight());
        Font font = config.getFontMini().deriveFont(Font.PLAIN, 16);
        lblTitle.setFont(font);
        titleField.setFont(font);
        ImageIcon iconSaveSmall = new ImageIcon(ImageIO.read(config.getImageSaveSmall().getInputStream()));
        btnSave.setIcon(iconSaveSmall);
    }

    @PostConstruct
    public void regEvent() {
        // 窗口关闭后重新初始化编辑器
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    remove(editor);
                    editor = new XDocEditor();
                    add(editor, BorderLayout.CENTER);
                    titleField.setText(titleField.getPrompt());
                    // 刷新主面板
                    DataRefreshEvent refreshEvent = new DataRefreshEvent(DataRefreshEvent.EventOf.REF_TREE);
                    context.publishEvent(refreshEvent);
                    clipsSaveDialog.initData();
                    // 清理数据
                    currId = null;
                    currType = null;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        // 保存按钮的处理
        this.btnSave.addActionListener(e -> {
            if (this.titleField.getText().trim().equals("") ||
                    this.titleField.getText().equals(titleField.getPrompt())) {
                JOptionPane.showMessageDialog(this, "记录的标题是不能为空的，一定要先填好他。", "保存", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            // 根据类型选择不同的存储对话框
            switch (currType) {
                case CLIPS:
                    clipsSaveDialog.showSave(contentPane, editor.getXDoc().toXml(), titleField.getText());
                    break;
                case DELAY:
                    dailysSaveDialog.showSave(contentPane, editor.getXDoc().toXml(), titleField.getText());
                    break;
            }
        });
    }

    /**
     * 准备数据，在展示窗口前，为窗口传入需要的数据。
     *
     * @param artleId 记录的id
     */
    public void prepare(Long artleId, GlobalType type) throws Exception {
        if (type == null) {
            type = SWestPane.getCurrType();
        }
        switch (type) {
            case CLIPS:
                this.currId = artleId;
                this.currType = type;
                if (artleId != null) {
                    Optional.ofNullable(clipsService.loadClipArtle(artleId)).ifPresent(artle -> {
                        try {
                            ClipsContent content = artle.getContent();
                            editor.setXDoc(new XDoc(content.getContent()));
                            titleField.setText(artle.getTitle());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
                // 初始化摘录的存储窗口
                clipsSaveDialog.prepare(artleId);
                break;
            case DELAY:
                this.currId = artleId;
                this.currType = type;
                if (artleId != null) {
                    Optional.ofNullable(dailyService.loadContent(artleId)).ifPresent(artle -> {
                        try {
                            if (artle.getCheckQuestion() == null || artle.getCheckQuestion().trim().equals("")) {
                                editor.setXDoc(new XDoc(artle.getContent().getContent()));
                                titleField.setText(artle.getTitle());
                                dailysSaveDialog.prepare(currId);
                            } else {
                                String answer = JOptionPane.showInputDialog(EditorForm.this, "请问" + artle.getCheckQuestion() + "?");
                                if (BCrypt.checkpw(answer, artle.getAnswer())) {
                                    editor.setXDoc(new XDoc(artle.getContent().getContent()));
                                    titleField.setText(artle.getTitle());
                                    dailysSaveDialog.prepare(currId);
                                } else {
                                    JOptionPane.showMessageDialog(EditorForm.this, "验证失败，你没有权限修改此记录。");
                                    this.currId = null;
                                }
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
                break;
        }
    }

}
