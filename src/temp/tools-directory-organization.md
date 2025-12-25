# Tools Directory Organization

This document organizes all tools registered in `PlanningFactory.java` by their directory locations.

## 1. Browser Tools (`tool/browser/`)

### Main Browser Tool
- **BrowserUseTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/BrowserUseTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser`

### Browser Operators (`tool/browser/browserOperators/`)
- **NavigateBrowserTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/browserOperators/NavigateBrowserTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators`

- **ClickBrowserTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/browserOperators/ClickBrowserTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators`

- **InputTextBrowserTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/browserOperators/InputTextBrowserTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators`

- **KeyEnterBrowserTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/browserOperators/KeyEnterBrowserTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators`

- **ScreenshotBrowserTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/browserOperators/ScreenshotBrowserTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators`

- **NewTabBrowserTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/browserOperators/NewTabBrowserTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators`

- **CloseTabBrowserTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/browserOperators/CloseTabBrowserTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators`

- **SwitchTabBrowserTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/browserOperators/SwitchTabBrowserTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators`

- **GetWebContentBrowserTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/browserOperators/GetWebContentBrowserTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators`

- **DownloadBrowserTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/browserOperators/DownloadBrowserTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators`

- **AbstractBrowserTool** (Base class)
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/browserOperators/AbstractBrowserTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators`

### Browser Service
- **ChromeDriverService**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/browser/ChromeDriverService.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.browser`

## 2. Database Tools (`tool/database/`)

### Main Database Tools
- **DatabaseWriteTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/database/DatabaseWriteTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.database`

- **DatabaseMetadataTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/database/DatabaseMetadataTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.database`

- **DatabaseTableToExcelTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/database/DatabaseTableToExcelTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.database`

- **UuidGenerateTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/database/UuidGenerateTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.database`

### Database Operators (`tool/database/databaseOperators/`)
- **ExecuteReadSqlTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/database/databaseOperators/ExecuteReadSqlTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.database.databaseOperators`

- **ExecuteReadSqlToJsonFileTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/database/databaseOperators/ExecuteReadSqlToJsonFileTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.database.databaseOperators`

### Database Service
- **DataSourceService**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/database/DataSourceService.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.database`

## 3. File/Text Operator Tools (`tool/textOperator/`)

### Global File Operators
- **GlobalFileReadOperator**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/textOperator/GlobalFileReadOperator.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.textOperator`

- **GlobalFileWriteOperator**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/textOperator/GlobalFileWriteOperator.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.textOperator`

- **FileImportOperator**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/textOperator/FileImportOperator.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.textOperator`

- **EnhancedGrep**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/textOperator/EnhancedGrep.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.textOperator`

### File Operators (`tool/textOperator/fileOperators/`)
- **ReadFileOperator**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/textOperator/fileOperators/ReadFileOperator.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators`

- **WriteFileOperator**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/textOperator/fileOperators/WriteFileOperator.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators`

- **DeleteFileOperator**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/textOperator/fileOperators/DeleteFileOperator.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators`

- **ReplaceFileOperator**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/textOperator/fileOperators/ReplaceFileOperator.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators`

- **SplitFileTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/textOperator/fileOperators/SplitFileTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators`

### Text File Service
- **TextFileService**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/textOperator/TextFileService.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.textOperator`

## 4. Directory Operator Tools (`tool/dirOperator/`)

### Directory Operators (`tool/dirOperator/dirOperators/`)
- **ListFilesTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/dirOperator/dirOperators/ListFilesTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.dirOperator.dirOperators`

- **GlobFilesTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/dirOperator/dirOperators/GlobFilesTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.dirOperator.dirOperators`

## 5. Parallel Execution Tools (`tool/mapreduce/`)

### Main Parallel Execution Tool
- **FileBasedParallelExecutionTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/mapreduce/FileBasedParallelExecutionTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.mapreduce`

### Parallel Operators (`tool/mapreduce/parallelOperators/`)
- **RegisterBatchExecutionTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/mapreduce/parallelOperators/RegisterBatchExecutionTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.mapreduce.parallelOperators`

- **StartParallelExecutionTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/mapreduce/parallelOperators/StartParallelExecutionTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.mapreduce.parallelOperators`

- **ClearPendingExecutionTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/mapreduce/parallelOperators/ClearPendingExecutionTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.mapreduce.parallelOperators`

### Parallel Execution Services
- **ParallelExecutionService**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/mapreduce/ParallelExecutionService.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.mapreduce`

- **FunctionRegistryService**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/mapreduce/FunctionRegistryService.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.mapreduce`

## 6. Conversion Tools (`tool/convertToMarkdown/`)

- **MarkdownConverterTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/convertToMarkdown/MarkdownConverterTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.convertToMarkdown`

- **PdfOcrProcessor**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/convertToMarkdown/PdfOcrProcessor.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.convertToMarkdown`

- **ImageOcrProcessor**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/convertToMarkdown/ImageOcrProcessor.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.convertToMarkdown`

## 7. Office Tools (`tool/office/`)

- **MarkdownToDocxTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/office/MarkdownToDocxTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.office`

## 8. Image Generation Tools (`tool/image/`)

- **ImageGenerationTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/image/ImageGenerationTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.image`

- **ImageGenerationProvider** (Interface)
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/image/ImageGenerationProvider.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.image`

## 9. Cron Tools (`tool/cron/`)

- **CronTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/cron/CronTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.cron`

## 10. Core Tools (`tool/`)

- **TerminateTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/TerminateTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool`

- **DebugTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/DebugTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool`

- **FormInputTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/FormInputTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool`

- **Bash**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/bash/Bash.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.bash`

- **ToolCallBiFunctionDef** (Base interface)
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/ToolCallBiFunctionDef.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool`

- **AbstractBaseTool** (Base class)
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/AbstractBaseTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool`

- **ToolExecuteResult** (Result class)
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/code/ToolExecuteResult.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.code`

## 11. MCP Tools (`mcp/model/vo/`)

- **McpTool**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/mcp/model/vo/McpTool.java`
  - Package: `com.alibaba.cloud.ai.lynxe.mcp.model.vo`

## 12. Supporting Services and Utilities

### Filesystem Utilities (`tool/filesystem/`)
- **UnifiedDirectoryManager**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/filesystem/UnifiedDirectoryManager.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.filesystem`

- **SymbolicLinkDetector**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/filesystem/SymbolicLinkDetector.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.filesystem`

- **GitIgnoreMatcher**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/filesystem/GitIgnoreMatcher.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.filesystem`

### Inner Storage (`tool/innerStorage/`)
- **SmartContentSavingService**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/innerStorage/SmartContentSavingService.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.innerStorage`

### Excel Processing (`tool/excelProcessor/`)
- **IExcelProcessingService** (Interface)
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/excelProcessor/IExcelProcessingService.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.excelProcessor`

### I18n (`tool/i18n/`)
- **ToolI18nService**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/i18n/ToolI18nService.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.i18n`

### Short URL (`tool/shortUrl/`)
- **ShortUrlService**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/shortUrl/ShortUrlService.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.shortUrl`

### JSX Generator (`tool/jsxGenerator/`)
- **JsxGeneratorOperator**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/jsxGenerator/JsxGeneratorOperator.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.jsxGenerator`
  - Note: Currently commented out in PlanningFactory

### PPT Generator (`tool/pptGenerator/`)
- **PptGeneratorService**
  - Path: `src/main/java/com/alibaba/cloud/ai/lynxe/tool/pptGenerator/PptGeneratorService.java`
  - Package: `com.alibaba.cloud.ai.lynxe.tool.pptGenerator`

## Summary by Directory Structure

```
src/main/java/com/alibaba/cloud/ai/lynxe/tool/
├── browser/
│   ├── BrowserUseTool.java
│   ├── ChromeDriverService.java
│   └── browserOperators/
│       ├── AbstractBrowserTool.java
│       ├── NavigateBrowserTool.java
│       ├── ClickBrowserTool.java
│       ├── InputTextBrowserTool.java
│       ├── KeyEnterBrowserTool.java
│       ├── ScreenshotBrowserTool.java
│       ├── NewTabBrowserTool.java
│       ├── CloseTabBrowserTool.java
│       ├── SwitchTabBrowserTool.java
│       ├── GetWebContentBrowserTool.java
│       └── DownloadBrowserTool.java
├── database/
│   ├── DatabaseWriteTool.java
│   ├── DatabaseMetadataTool.java
│   ├── DatabaseTableToExcelTool.java
│   ├── UuidGenerateTool.java
│   ├── DataSourceService.java
│   └── databaseOperators/
│       ├── ExecuteReadSqlTool.java
│       └── ExecuteReadSqlToJsonFileTool.java
├── textOperator/
│   ├── GlobalFileReadOperator.java
│   ├── GlobalFileWriteOperator.java
│   ├── FileImportOperator.java
│   ├── EnhancedGrep.java
│   ├── TextFileService.java
│   └── fileOperators/
│       ├── ReadFileOperator.java
│       ├── WriteFileOperator.java
│       ├── DeleteFileOperator.java
│       ├── ReplaceFileOperator.java
│       └── SplitFileTool.java
├── dirOperator/
│   └── dirOperators/
│       ├── ListFilesTool.java
│       └── GlobFilesTool.java
├── mapreduce/
│   ├── FileBasedParallelExecutionTool.java
│   ├── ParallelExecutionService.java
│   ├── FunctionRegistryService.java
│   └── parallelOperators/
│       ├── RegisterBatchExecutionTool.java
│       ├── StartParallelExecutionTool.java
│       └── ClearPendingExecutionTool.java
├── convertToMarkdown/
│   ├── MarkdownConverterTool.java
│   ├── PdfOcrProcessor.java
│   └── ImageOcrProcessor.java
├── office/
│   └── MarkdownToDocxTool.java
├── image/
│   ├── ImageGenerationTool.java
│   └── ImageGenerationProvider.java
├── cron/
│   └── CronTool.java
├── bash/
│   └── Bash.java
├── filesystem/
│   ├── UnifiedDirectoryManager.java
│   ├── SymbolicLinkDetector.java
│   └── GitIgnoreMatcher.java
├── innerStorage/
│   └── SmartContentSavingService.java
├── excelProcessor/
│   └── IExcelProcessingService.java
├── i18n/
│   └── ToolI18nService.java
├── shortUrl/
│   └── ShortUrlService.java
├── jsxGenerator/
│   └── JsxGeneratorOperator.java
├── pptGenerator/
│   └── PptGeneratorService.java
├── TerminateTool.java
├── DebugTool.java
├── FormInputTool.java
├── ToolCallBiFunctionDef.java
└── AbstractBaseTool.java
```

## Total Tool Count

- **Browser Tools**: 11 tools (1 main + 10 operators)
- **Database Tools**: 5 tools (4 main + 2 operators)
- **File/Text Operator Tools**: 9 tools (4 global + 5 file operators)
- **Directory Operator Tools**: 2 tools
- **Parallel Execution Tools**: 4 tools (1 main + 3 operators)
- **Conversion Tools**: 3 tools
- **Office Tools**: 1 tool
- **Image Generation Tools**: 1 tool
- **Cron Tools**: 1 tool
- **Core Tools**: 5 tools
- **MCP Tools**: 1 tool (dynamic)

**Total: 42+ registered tools** (excluding commented out tools and supporting services)

