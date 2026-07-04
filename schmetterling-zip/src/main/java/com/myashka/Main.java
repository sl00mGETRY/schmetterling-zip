package com.myashka;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.*;

public class Main extends Application {

    private static final byte[] FAKE_RAR_HEADER = "Rar!\u001a\u0007\u0000".getBytes();
    private static final byte[] MARKER = "MYASH!".getBytes();

    private final List<File> publicFiles = new ArrayList<>();
    private final List<File> privateFiles = new ArrayList<>();
    private File archiveToExtract = null;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Schmetterling-ZIP");

        TabPane tabPane = new TabPane();

        Tab packTab = new Tab("Packer");
        packTab.setClosable(false);
        VBox packBox = new VBox(10);
        packBox.setPadding(new Insets(20));
        packBox.setAlignment(Pos.CENTER);

        Label packTitle = new Label("Create Polyglot Archive");
        packTitle.getStyleClass().add("neon-title");

        Label pubLabel = new Label("Public Files: None");
        Button btnSelectPub = new Button("Select Public Files");
        btnSelectPub.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            List<File> selected = chooser.showOpenMultipleDialog(stage);
            if (selected != null) {
                publicFiles.addAll(selected);
                pubLabel.setText("Public Files: " + publicFiles.size() + " items selected");
            }
        });

        Label privLabel = new Label("Secret Files: None");
        Button btnSelectPriv = new Button("Select Secret Files");
        btnSelectPriv.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            List<File> selected = chooser.showOpenMultipleDialog(stage);
            if (selected != null) {
                privateFiles.addAll(selected);
                privLabel.setText("Secret Files: " + privateFiles.size() + " items selected");
            }
        });

        Button btnPack = new Button("Pack to .myash");
        btnPack.setOnAction(e -> packArchive(stage, pubLabel, privLabel));

        packBox.getChildren().addAll(packTitle, btnSelectPub, pubLabel, btnSelectPriv, privLabel, new Separator(), btnPack);
        packTab.setContent(packBox);

        Tab unpackTab = new Tab("Extractor");
        unpackTab.setClosable(false);
        VBox unpackBox = new VBox(10);
        unpackBox.setPadding(new Insets(20));
        unpackBox.setAlignment(Pos.CENTER);

        Label unpackTitle = new Label("Extract Archive");
        unpackTitle.getStyleClass().add("neon-title");

        Label archLabel = new Label("No archive selected (Supports .myash, .zip, .jar)");
        Button btnOpen = new Button("Open Archive");
        btnOpen.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archives", "*.myash", "*.zip", "*.jar"));
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                archiveToExtract = file;
                archLabel.setText("Selected: " + file.getName());
            }
        });

        Button btnExtPub = new Button("Extract Public Sector");
        btnExtPub.setOnAction(e -> extractArchive(stage, false));

        Button btnExtPriv = new Button("Extract Secret (.myash) Sector");
        btnExtPriv.setOnAction(e -> extractArchive(stage, true));

        unpackBox.getChildren().addAll(unpackTitle, btnOpen, archLabel, new Separator(), btnExtPub, btnExtPriv);
        unpackTab.setContent(unpackBox);

        tabPane.getTabs().addAll(packTab, unpackTab);

        Scene scene = new Scene(tabPane, 650, 450);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    private void packArchive(Stage stage, Label pubL, Label privL) {
        if (publicFiles.isEmpty() && privateFiles.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Select at least some files, digga!");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("archive.myash");
        File saveFile = chooser.showSaveDialog(stage);
        if (saveFile == null) return;

        try (FileOutputStream fos = new FileOutputStream(saveFile)) {
            fos.write(FAKE_RAR_HEADER);

            if (!publicFiles.isEmpty()) {
                byte[] pubZip = createZipBuffer(publicFiles);
                fos.write(pubZip);
            }

            fos.write(MARKER);

            if (!privateFiles.isEmpty()) {
                byte[] privZip = createZipBuffer(privateFiles);
                fos.write(privZip);
            }

            showAlert(Alert.AlertType.INFORMATION, "Success", "Polyglot archive created successfully!");
            publicFiles.clear();
            privateFiles.clear();
            pubL.setText("Public Files: None");
            privL.setText("Secret Files: None");

        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
        }
    }

    private byte[] createZipBuffer(List<File> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (File file : files) {
                addFileToZip(zos, file, file.getName());
            }
        }
        return baos.toByteArray();
    }

    private void addFileToZip(ZipOutputStream zos, File file, String name) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addFileToZip(zos, child, name + "/" + child.getName());
                }
            }
        } else {
            ZipEntry entry = new ZipEntry(name);
            zos.putNextEntry(entry);
            Files.copy(file.toPath(), zos);
            zos.closeEntry();
        }
    }

    private void extractArchive(Stage stage, boolean isPrivate) {
        if (archiveToExtract == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "Open an archive first!");
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        File destDir = chooser.showDialog(stage);
        if (destDir == null) return;

        try {
            byte[] data = Files.readAllBytes(archiveToExtract.toPath());
            int markerIdx = findMarker(data);

            byte[] targetBytes;
            if (isPrivate) {
                targetBytes = (markerIdx == -1) ? data : cutBytes(data, markerIdx + MARKER.length, data.length);
            } else {
                targetBytes = (markerIdx == -1) ? data : cutBytes(data, FAKE_RAR_HEADER.length, markerIdx);
            }

            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(targetBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path outPath = destDir.toPath().resolve(entry.getName()).normalize();
                    if (!outPath.startsWith(destDir.toPath())) continue;

                    if (entry.isDirectory()) {
                        Files.createDirectories(outPath);
                    } else {
                        Files.createDirectories(outPath.getParent());
                        Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            showAlert(Alert.AlertType.INFORMATION, "Success", "Extraction complete!");

        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
        }
    }

    private int findMarker(byte[] data) {
        for (int i = 0; i <= data.length - MARKER.length; i++) {
            boolean found = true;
            for (int j = 0; j < MARKER.length; j++) {
                if (data[i + j] != MARKER[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return -1;
    }

    private byte[] cutBytes(byte[] src, int start, int end) {
        byte[] dest = new byte[end - start];
        System.arraycopy(src, start, dest, 0, dest.length);
        return dest;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}