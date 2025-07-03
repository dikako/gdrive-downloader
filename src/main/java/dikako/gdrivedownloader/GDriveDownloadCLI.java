package dikako.gdrivedownloader;

import java.io.FileInputStream;
import java.nio.file.Paths;

/**
 * Command-line interface for downloading a file from Google Drive using a service account.
 */
public class GDriveDownloadCLI {

  /**
   * Entry point of the CLI program.
   * <p>
   * Usage:
   * {@code java GDriveDownloadCLI serviceAccountJsonPath fileId downloadDirectory}
   *
   * @param args Command-line arguments:
   *             args[0] - Path to the service account JSON file
   *             args[1] - Google Drive file ID to download
   *             args[2] - Directory to save the downloaded file
   */
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
