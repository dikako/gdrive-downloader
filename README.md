# GDriveDownloader

![Maven Central](https://img.shields.io/maven-central/v/io.github.dikako/gdrive-downloader)
![License](https://img.shields.io/github/license/dikako/gdrive-downloader)

**GDriveDownloader** is a lightweight Java library for downloading files from Google Drive using a **service account**, with built-in support for:
- File download by ID or name
- Pattern-based file matching (`contains`, `regex`)
- Shared Drive (Team Drive) support
- Smart export of Google Docs (e.g. `.docx`, `.xlsx`, `.pptx`)

---

## 📦 Installation

Add the following to your `build.gradle`:

```groovy
dependencies {
    implementation 'io.github.dikako:gdrive-downloader:1.0.0'
}
```

## 🔧 Prerequisites

1. Enable Google Drive API in your [Google Cloud Console](https://console.cloud.google.com/apis/library/drive.googleapis.com).
2. Create a Service Account and download its JSON credentials.
3. Share your Google Drive files or folders with the service account email.

## 🚀 Usage

### Initialize the downloader

```java
GDriveDownloader downloader = new GDriveDownloader(new FileInputStream("service-account.json"));
```

### Download by File ID

```java
downloader.downloadFileById("1aBcD1234567", Paths.get("downloads"));
```

### Download by Exact File Name

```java
downloader.downloadFileByName("myfile.txt", Paths.get("downloads"));
```

### Download by Partial Name Match

```java
downloader.downloadFileByNameContains("report", Paths.get("downloads"));
```

### Download by Regex Match

```java
downloader.downloadFileByRegex("^backup_\\d{4}\\.zip$", Paths.get("downloads"));
```

### Download from Shared Drive folder (partial name)

```java
downloader.downloadFileByNameContainsInFolderPath(
    "MySharedDrive/Reports/2024",
    "summary",
    Paths.get("downloads")
);
```

```java
// For setAcknowledgeAbuse
downloader.downloadFileByNameContainsInFolderPath(
    "MySharedDrive/Reports/2024",
    "summary",
    Paths.get("downloads"),
    true
);
```

### Download from Shared Drive folder (regex)

```java
downloader.downloadFileByRegexInFolderPath(
    "MySharedDrive/Exports",
    ".*\\.csv",
    Paths.get("downloads")
);
```

```java
// For setAcknowledgeAbuse
downloader.downloadFileByRegexInFolderPath(
    "MySharedDrive/Exports",
    ".*\\.csv",
    Paths.get("downloads"),
    true
);
```

## 🧠 Smart Export Support

`downloadFileSmart()` is automatically used in folder-based download methods, handling:

* Google Docs → `.docx`
* Google Sheets → `.xlsx`
* Google Slides → `.pptx`
* Regular files → downloaded as-is

## 🗂 Supported MIME Type Conversions

| Google MIME Type                           | Exported As                                                                         |
| ------------------------------------------ | ----------------------------------------------------------------------------------- |
| `application/vnd.google-apps.document`     | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (.docx)   |
| `application/vnd.google-apps.spreadsheet`  | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (.xlsx)         |
| `application/vnd.google-apps.presentation` | `application/vnd.openxmlformats-officedocument.presentationml.presentation` (.pptx) |

## 📁 Shared Drive Folder Path Format

Use format:

```text
SharedDriveName/Subfolder1/Subfolder2/...
```

Example:

```text
"FinanceDrive/MonthlyReports/June"
```

## 🛡 Permissions

Make sure the service account has access to the Drive files/folders you want to download. This means sharing the files or folders directly with the service account email.

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙋 FAQ

#### What happens if a file is not found?

An exception will be thrown (e.g., FileNotFoundException).

#### Will folders be created automatically?

Yes. If the output folder does not exist, it will be created.

## 👨‍💻 Author

Fransiskus Andika Setiawan ([@dikako](https://github.com/dikako))

## ⭐️ Stargazers

If you find this library useful, please consider giving it a ⭐️ on GitHub!

```text

Let me know if you want the `README.md` to include usage for Maven, Javadoc links, or publishing instructions.
```
