package dikako.gdrivedownloader;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Utility class for downloading files from Google Drive using a service account.
 */
public class GDriveDownloader {
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private final Drive driveService;

  private static final String GOOGLE_DOC = "application/vnd.google-apps.document";
  private static final String GOOGLE_SHEET = "application/vnd.google-apps.spreadsheet";
  private static final String GOOGLE_SLIDE = "application/vnd.google-apps.presentation";


  /**
   * Constructs a GDriveDownloader instance using the provided service account credentials.
   *
   * @param serviceAccountJson InputStream of the service account JSON file.
   * @throws IOException if there's an error reading the credentials.
   * @throws GeneralSecurityException if there's a security error initializing the client.
   */
  public GDriveDownloader(InputStream serviceAccountJson) throws IOException, GeneralSecurityException {
    GoogleCredentials credentials = GoogleCredentials
        .fromStream(serviceAccountJson)
        .createScoped(List.of("https://www.googleapis.com/auth/drive.readonly"));

    HttpRequestInitializer timeoutRequestInitializer = request -> {
      new HttpCredentialsAdapter(credentials).initialize(request);
      request.setConnectTimeout(5 * 60 * 1000); // 5 minutes
      request.setReadTimeout(5 * 60 * 1000);    // 5 minutes
    };

    this.driveService = new Drive.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        JSON_FACTORY,
        timeoutRequestInitializer
    ).setApplicationName("GDriveDownloader").build();
  }

  /**
   * Downloads a file from Google Drive using its file ID.
   *
   * @param fileId ID of the file to download.
   * @param outputPath Path to save the downloaded file.
   * @return The path to the downloaded file as a string.
   * @throws IOException if the file cannot be downloaded.
   */
  public String downloadFileById(String fileId, Path outputPath) throws IOException {
    File fileMetadata = driveService.files().get(fileId).setFields("name").execute();
    String fileName = fileMetadata.getName();

    java.io.File targetFile = outputPath.resolve(fileName).toFile();
    try (
        InputStream inputStream = driveService.files().get(fileId).executeMediaAsInputStream();
        FileOutputStream outputStream = new FileOutputStream(targetFile)
    ) {
      inputStream.transferTo(outputStream);
    }

    return fileName;
  }

  /**
   * Downloads a file by its exact name from Google Drive.
   *
   * @param fileName The exact file name to search for.
   * @param outputPath The path where the file will be saved locally.
   * @return The path of the downloaded file.
   * @throws IOException If an error occurs during the download process.
   */
  public String downloadFileByName(String fileName, Path outputPath) throws IOException {
    String query = String.format("name='%s' and trashed=false", fileName);

    FileList result = driveService.files().list()
        .setQ(query)
        .setFields("files(id, name)")
        .setPageSize(1) // get only the first match
        .execute();

    List<File> files = result.getFiles();
    if (files == null || files.isEmpty()) {
      throw new FileNotFoundException("No file found with name: " + fileName);
    }

    File matchedFile = files.get(0); // return first match
    return downloadFileById(matchedFile.getId(), outputPath);
  }

  /**
   * Downloads the first file that contains the given partial name from Google Drive.
   *
   * @param partialName Partial name to search for.
   * @param outputPath The local path where the file will be saved.
   * @return The path to the downloaded file.
   * @throws IOException If an I/O error occurs during download.
   */
  public String downloadFileByNameContains(String partialName, Path outputPath) throws IOException {
    FileList result = driveService.files().list()
        .setQ("trashed = false")
        .setFields("files(id, name)")
        .setPageSize(1000)
        .execute();

    Optional<File> matchedFile = result.getFiles()
        .stream()
        .filter(f -> f.getName().contains(partialName))
        .findFirst();

    if (matchedFile.isEmpty()) {
      throw new FileNotFoundException("No file found containing: " + partialName);
    }

    return downloadFileById(matchedFile.get().getId(), outputPath);
  }

  /**
   * Downloads the first file that matches the given regex pattern from Google Drive.
   *
   * @param regex Regular expression to match file names.
   * @param outputPath The local path to save the downloaded file.
   * @return The path to the downloaded file.
   * @throws IOException If an I/O error occurs during download.
   */
  public String downloadFileByRegex(String regex, Path outputPath) throws IOException {
    Pattern pattern = Pattern.compile(regex);

    FileList result = driveService.files().list()
        .setQ("trashed = false")
        .setFields("files(id, name)")
        .setPageSize(1000)
        .execute();

    Optional<File> matchedFile = result.getFiles()
        .stream()
        .filter(f -> pattern.matcher(f.getName()).matches())
        .findFirst();

    if (matchedFile.isEmpty()) {
      throw new FileNotFoundException("No file found matching regex: " + regex);
    }

    return downloadFileById(matchedFile.get().getId(), outputPath);
  }

  /**
   * Downloads the first file whose name contains the given partial string
   * in the folder with the specified name.
   *
   * @param folderPath      Name of the Drive folder to search in.
   * @param partialFileName Partial file name to search for.
   * @param outputPath      Local path to save the downloaded file.
   * @return The name of the downloaded file.
   * @throws IOException If folder or file is not found.
   */
  public String downloadFileByNameContainsInFolderPath(String folderPath, String partialFileName, Path outputPath, boolean... ackAbuseFile) throws IOException {
    String[] parts = folderPath.split("/");
    if (parts.length < 2) {
      throw new IllegalArgumentException("Folder path must include shared drive and at least one folder (e.g., 'DriveName/Folder')");
    }

    String driveName = parts[0];
    String driveId = findSharedDriveIdByName(driveName);
    if (driveId == null) {
      throw new FileNotFoundException("Shared Drive not found: " + driveName);
    }

    String folderId = resolveFolderPathToId(folderPath); // this now also uses driveId internally
    if (folderId == null) {
      throw new FileNotFoundException("Folder path not found: " + folderPath);
    }

    String query = String.format("trashed = false and '%s' in parents", folderId);

    FileList fileList = driveService.files().list()
        .setQ(query)
        .setSupportsAllDrives(true)
        .setIncludeItemsFromAllDrives(true)
        .setCorpora("drive")
        .setDriveId(driveId)
        .setFields("files(id, name, mimeType)")
        .setPageSize(1000)
        .execute();

    return fileList.getFiles().stream()
        .peek(f -> System.out.println("[File Match] Checking: " + f.getName()))
        .filter(file -> file.getName().contains(partialFileName))
        .findFirst()
        .map(file -> {
          try {
            return downloadFileSmart(file.getId(), outputPath, ackAbuseFile);
          } catch (IOException e) {
            throw new RuntimeException("Failed to download file: " + file.getName(), e);
          }
        })
        .orElseThrow(() -> new FileNotFoundException(
            "No file found containing: '" + partialFileName + "' in folder path: '" + folderPath + "'"
        ));
  }

  /**
   * Downloads the first file that matches the given regex within a specific folder.
   *
   * @param folderPath The path of the folder to search in.
   * @param regex The regular expression to match file names.
   * @param outputPath The local path where the file will be saved.
   * @return The path of the downloaded file.
   * @throws IOException If an I/O error occurs.
   */
  public String downloadFileByRegexInFolderPath(String folderPath, String regex, Path outputPath, boolean... ackAbuseFile) throws IOException {
    Pattern pattern = Pattern.compile(regex);

    // Extract drive name from path
    String[] parts = folderPath.split("/");
    if (parts.length < 2) {
      throw new IllegalArgumentException("Folder path must be in format: 'SharedDriveName/Subfolder/...'");
    }

    String driveName = parts[0];
    String driveId = findSharedDriveIdByName(driveName);
    if (driveId == null) {
      throw new FileNotFoundException("Shared drive not found: " + driveName);
    }

    String folderId = resolveFolderPathToId(folderPath); // internally uses driveId
    if (folderId == null) {
      throw new FileNotFoundException("Folder path not found: " + folderPath);
    }

    String query = String.format("trashed = false and '%s' in parents", folderId);

    FileList fileList = driveService.files().list()
        .setQ(query)
        .setDriveId(driveId)
        .setCorpora("drive")
        .setSupportsAllDrives(true)
        .setIncludeItemsFromAllDrives(true)
        .setFields("files(id, name, mimeType)")
        .setPageSize(1000)
        .execute();

    Optional<File> matchedFile = fileList.getFiles().stream()
        .peek(file -> System.out.println("[Regex Match] Checking: " + file.getName()))
        .filter(file -> pattern.matcher(file.getName()).matches())
        .findFirst();

    if (matchedFile.isEmpty()) {
      throw new FileNotFoundException("No file found matching regex: " + regex + " in folder path: " + folderPath);
    }

    System.out.println("Downloaded file: " + matchedFile.get().getName() + " in progress...");
    return downloadFileSmart(matchedFile.get().getId(), outputPath, ackAbuseFile);
  }

  /**
   * Finds the ID of a folder by its name.
   * Returns the first match found.
   *
   * @param folderName Name of the folder.
   * @return The folder ID, or null if not found.
   * @throws IOException On Drive API failure.
   */
  private String findFolderIdByName(String folderName) throws IOException {
    String query = String.format("mimeType = 'application/vnd.google-apps.folder' and name = '%s' and trashed = false", folderName);

    FileList folders = driveService.files().list()
        .setQ(query)
        .setFields("files(id, name)")
        .setPageSize(1)
        .execute();

    if (folders.getFiles().isEmpty()) {
      return null;
    }

    return folders.getFiles().get(0).getId();
  }

  /**
   * Downloads any file — binary or Google Docs — and auto-exports if needed.
   *
   * @param fileId     Google Drive file ID
   * @param outputPath Local output directory
   * @return Name of the downloaded file
   * @throws IOException if download/export fails
   */
  public String downloadFileSmart(String fileId, Path outputPath, boolean... ackAbuseFile) throws IOException {
    // Make sure output directory exists
    java.io.File outputDir = outputPath.toFile();
    if (!outputDir.exists()) {
      boolean created = outputDir.mkdirs();
      if (!created) {
        throw new IOException("Failed to create output directory: " + outputDir.getAbsolutePath());
      }
      System.out.println("[Output Folder] ✅ Created: " + outputDir.getAbsolutePath());
    }

    File fileMetadata = driveService.files()
        .get(fileId)
        .setFields("name, mimeType, size")
        .setSupportsAllDrives(true)
        .execute();

    String fileName = fileMetadata.getName();
    String mimeType = fileMetadata.getMimeType();

    java.io.File targetFile;
    InputStream inputStream = switch (mimeType) {
      case GOOGLE_DOC -> {
        fileName += ".docx";
        yield driveService.files().export(fileId, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .executeMediaAsInputStream();
      }
      case GOOGLE_SHEET -> {
        fileName += ".xlsx";
        yield driveService.files().export(fileId, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .executeMediaAsInputStream();
      }
      case GOOGLE_SLIDE -> {
        fileName += ".pptx";
        yield driveService.files().export(fileId, "application/vnd.openxmlformats-officedocument.presentationml.presentation")
            .executeMediaAsInputStream();
      }
      default -> driveService.files()
          .get(fileId)
          .setAcknowledgeAbuse(ackAbuseFile.length > 0 && ackAbuseFile[0])
          .executeMediaAsInputStream();
    };

    targetFile = outputPath.resolve(fileName).toFile();
    try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
      inputStream.transferTo(outputStream);
    }

    return fileName;
  }

  private String resolveFolderPathToId(String folderPath) throws IOException {
    String[] parts = folderPath.split("/");
    if (parts.length == 0) return null;

    // First part is assumed to be the Shared Drive name
    String driveName = parts[0];
    String driveId = findSharedDriveIdByName(driveName);
    if (driveId == null) return null;

    String parentId = null;

    for (int i = 1; i < parts.length; i++) {
      String folderName = parts[i];

      String query = String.format("mimeType = 'application/vnd.google-apps.folder' and name = '%s' and trashed = false", folderName);
      if (parentId != null) {
        query += String.format(" and '%s' in parents", parentId);
      }

      FileList folders = driveService.files().list()
          .setQ(query)
          .setSupportsAllDrives(true)
          .setIncludeItemsFromAllDrives(true)
          .setCorpora("drive")
          .setDriveId(driveId)
          .setFields("files(id, name)")
          .setPageSize(1)
          .execute();

      if (folders.getFiles().isEmpty()) {
        System.err.println("[Folder Lookup] ❌ Not found: '" + folderName);
        return null;
      }

      File folder = folders.getFiles().get(0);
      System.out.println("[Folder Lookup] ✅ Found: '" + folderName);
      parentId = folder.getId();
    }

    return parentId;
  }


  private String findSharedDriveIdByName(String driveName) throws IOException {
    Drive.Drives.List request = driveService.drives().list()
        .setPageSize(100);

    var drives = request.execute().getDrives();

    for (var drive : drives) {
      if (drive.getName().equalsIgnoreCase(driveName)) {
        System.out.println("[Drive Lookup] ✅ Found shared drive: " + driveName);
        return drive.getId();
      }
    }

    System.err.println("[Drive Lookup] ❌ Shared drive not found: " + driveName);
    return null;
  }
}
