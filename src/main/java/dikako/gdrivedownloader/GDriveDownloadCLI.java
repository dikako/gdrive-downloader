package dikako.gdrivedownloader;

import java.io.FileInputStream;
import java.nio.file.Paths;

public class GDriveDownloadCLI {
  public static void main(String[] args) {
    if (args.length != 3) {
      System.out.println("Usage: java DriverDownloadCLI <serviceAccountJsonPath> <fileId> <downloadDirectory>");
      return;
    }

    String serviceAccountJson = args[0];
    String fileId = args[1];
    String outputDir = args[2];

    try (FileInputStream fis = new FileInputStream(serviceAccountJson)) {
      GDriveDownloader downloader = new GDriveDownloader(fis);
      String downloadedFile = downloader.downloadFileById(fileId, Paths.get(outputDir));
      System.out.println("Downloaded file: " + downloadedFile);
    } catch (Exception e) {
      System.err.println("Failed to download file: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
