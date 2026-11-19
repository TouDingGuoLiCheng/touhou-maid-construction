package com.touhoumaid.construction.client;

import com.touhoumaid.construction.project.SchematicFiles;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Native picker: file or folder → copy into schematics and return imported names. */
@OnlyIn(Dist.CLIENT)
public final class SchematicImportClient {
    private SchematicImportClient() {
    }

    /** Opens a chooser that accepts a single .litematic file or a folder of them. */
    public static void pickFileOrFolder(Consumer<List<String>> onImportedNames, Runnable onFinished) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(null));

        CompletableFuture.runAsync(() -> {
            JFrame owner = createTopmostOwner();
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("选择投影文件或文件夹");
                chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                chooser.setAcceptAllFileFilterUsed(true);
                chooser.setFileFilter(new FileNameExtensionFilter("Litematic (*.litematic)", "litematic"));
                int result = chooser.showOpenDialog(owner);
                if (result != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                File selected = chooser.getSelectedFile();
                if (selected == null) {
                    return;
                }
                List<String> names = new ArrayList<>();
                if (selected.isDirectory()) {
                    names.addAll(SchematicFiles.copyFolderLitematics(selected.toPath()));
                } else if (Files.isRegularFile(selected.toPath())) {
                    names.add(SchematicFiles.copyIntoSchematics(selected.toPath()));
                }
                List<String> imported = List.copyOf(names);
                mc.execute(() -> onImportedNames.accept(imported));
            } catch (Exception e) {
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("导入失败: " + e.getMessage()),
                                false);
                    }
                });
            } finally {
                owner.dispose();
                if (onFinished != null) {
                    mc.execute(onFinished);
                }
            }
        });
    }

    private static JFrame createTopmostOwner() {
        JFrame owner = new JFrame();
        owner.setAlwaysOnTop(true);
        owner.setType(Window.Type.UTILITY);
        owner.setUndecorated(true);
        owner.setSize(0, 0);
        owner.setLocationRelativeTo(null);
        owner.setVisible(true);
        owner.toFront();
        owner.requestFocus();
        return owner;
    }
}
