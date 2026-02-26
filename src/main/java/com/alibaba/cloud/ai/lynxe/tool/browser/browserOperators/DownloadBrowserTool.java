/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.ToolStateInfo;
import com.alibaba.cloud.ai.lynxe.tool.browser.service.BrowserUseCommonService;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

/**
 * Download browser tool that clicks a download link/button (by element index) and saves
 * the file to the plan's downloads directory. Handles two outcomes from the click:
 * <ul>
 * <li><b>Browser download</b>: uses {@link Download#saveAs(Path)} when the link triggers
 * a download.</li>
 * <li><b>New tab with document</b>: when the link opens the file (e.g. PDF) in a new tab
 * instead of downloading, fetches the tab's URL via the browser context (same cookies)
 * and saves the response body to the downloads directory, then closes the new tab.</li>
 * </ul>
 */
public class DownloadBrowserTool extends AbstractBrowserTool<DownloadBrowserTool.DownloadInput> {

	private static final Logger log = LoggerFactory.getLogger(DownloadBrowserTool.class);

	private static final String TOOL_NAME = "download-browser";

	private static final String DOWNLOADS_SUBDIR = "downloads";

	private static boolean isDocumentLikeUrl(String url) {
		if (url == null || url.isBlank() || "about:blank".equals(url)) {
			return false;
		}
		String lower = url.toLowerCase();
		return lower.contains(".pdf") || url.contains("cdn.") || url.contains("/data/") || url.contains("bulletin/");
	}

	/**
	 * Timeout for waiting for either a download event or a popup (new tab) after the
	 * click. When the link opens a PDF in a new tab, no download event fires; we wait for
	 * the popup instead.
	 */
	private static final int DOWNLOAD_OR_POPUP_TIMEOUT_MS = 60_000; // 1 minute

	private final ToolI18nService toolI18nService;

	private final UnifiedDirectoryManager unifiedDirectoryManager;

	/**
	 * Input for download operations: either a direct URL to download or an element index
	 * from ARIA snapshot (the link/button that triggers the download).
	 */
	public static class DownloadInput {

		private String url;

		private Integer index;

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public Integer getIndex() {
			return index;
		}

		public void setIndex(Integer index) {
			this.index = index;
		}

	}

	public DownloadBrowserTool(BrowserUseCommonService browserUseTool, UnifiedDirectoryManager unifiedDirectoryManager,
			ToolI18nService toolI18nService) {
		super(browserUseTool);
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public String getServiceGroup() {
		return "bw";
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription(TOOL_NAME);
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters(TOOL_NAME);
	}

	@Override
	public Class<DownloadInput> getInputType() {
		return DownloadInput.class;
	}

	@Override
	public ToolExecuteResult run(DownloadInput input) {
		String inputUrl = input != null ? input.getUrl() : null;
		Integer index = input != null ? input.getIndex() : null;
		log.info("DownloadBrowserTool request: url={}, index={}", inputUrl, index);
		try {
			ToolExecuteResult validation = validateDriver();
			if (validation != null) {
				return validation;
			}

			String rootPlanId = getRootPlanId();
			if (rootPlanId == null || rootPlanId.isBlank()) {
				return new ToolExecuteResult("Error: root plan context is not available for saving the download");
			}

			Path downloadDir = unifiedDirectoryManager.getRootPlanDirectory(rootPlanId).resolve(DOWNLOADS_SUBDIR);
			try {
				unifiedDirectoryManager.ensureDirectoryExists(downloadDir);
			}
			catch (IOException e) {
				log.error("Failed to create download directory: {}", e.getMessage());
				return new ToolExecuteResult("Failed to create download directory: " + e.getMessage());
			}

			// Direct download by URL: prefer when url is provided (or when both url and
			// index set).
			String urlToDownload = (inputUrl != null && !inputUrl.isBlank()) ? inputUrl.trim() : null;
			if (urlToDownload != null) {
				if (!urlToDownload.startsWith("http://") && !urlToDownload.startsWith("https://")) {
					urlToDownload = "https://" + urlToDownload;
				}
				log.info("Downloading directly from URL: {}", urlToDownload);
				return saveFromUrl(urlToDownload, getCurrentPage(), downloadDir);
			}

			// Click element by index to trigger download or open in new tab.
			if (index == null) {
				return new ToolExecuteResult(
						"Error: provide either url (direct download) or index (element index of the download link/button to click)");
			}

			if (!elementExistsByIdx(index)) {
				return new ToolExecuteResult("Element with index " + index + " not found in ARIA snapshot");
			}

			Page page = getCurrentPage();
			Locator locator = getLocatorByIdx(index);
			if (locator == null) {
				return new ToolExecuteResult("Failed to create locator for element with index " + index);
			}

			// Wait for either a download event or a new tab (popup). Many "download"
			// links
			// (e.g. PDFs on fund sites) open the file in a new tab instead of triggering
			// a
			// download; we handle both with one click.
			CompletableFuture<Download> downloadFuture = new CompletableFuture<>();
			CompletableFuture<Page> popupFuture = new CompletableFuture<>();
			page.onDownload(d -> {
				if (!downloadFuture.isDone()) {
					downloadFuture.complete(d);
				}
			});
			page.onPopup(p -> {
				if (!popupFuture.isDone()) {
					popupFuture.complete(p);
				}
			});
			// Capture pages and URLs before click so we can detect new tabs or same-tab
			// navigation when onPopup does not fire (same pattern as
			// AbstractBrowserTool.clickAndSwitchToNewTabIfOpened).
			List<Page> pagesBeforeClick = page.context().pages();
			Set<String> urlsBeforeClick = pagesBeforeClick.stream().map(Page::url).collect(Collectors.toSet());

			Runnable clickAction = () -> {
				locator.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
				locator.click(new Locator.ClickOptions().setTimeout(getElementTimeoutMs()));
			};
			clickAction.run();

			// Poll for new/document page in case onPopup never fires (e.g. some fund
			// sites).
			CompletableFuture<Page> pageDiffFuture = new CompletableFuture<>();
			ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "download-page-diff-poller");
				t.setDaemon(true);
				return t;
			});
			poller.scheduleAtFixedRate(() -> {
				if (downloadFuture.isDone() || popupFuture.isDone() || pageDiffFuture.isDone()) {
					return;
				}
				try {
					List<Page> pages = page.context().pages();
					for (Page p : pages) {
						if (p == null || p.isClosed())
							continue;
						String u = p.url();
						if (u != null && !u.isBlank() && !urlsBeforeClick.contains(u)) {
							pageDiffFuture.complete(p);
							return;
						}
						if (isDocumentLikeUrl(u)) {
							pageDiffFuture.complete(p);
							return;
						}
					}
				}
				catch (Exception ignored) {
				}
			}, 2000, 2000, TimeUnit.MILLISECONDS);

			Object result;
			try {
				result = CompletableFuture.anyOf(downloadFuture, popupFuture, pageDiffFuture)
					.get(DOWNLOAD_OR_POPUP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			}
			catch (TimeoutException e) {
				// Fallback: on some sites the new tab does not fire onPopup; detect by
				// URL
				// diff (same pattern as
				// AbstractBrowserTool.clickAndSwitchToNewTabIfOpened).
				List<Page> pagesAfter = page.context().pages();
				List<Page> pagesWithNewUrl = pagesAfter.stream()
					.filter(p -> p.url() != null && !p.url().isBlank() && !urlsBeforeClick.contains(p.url()))
					.collect(Collectors.toList());
				Page pageWithNewUrl = pagesWithNewUrl.isEmpty() ? null : pagesWithNewUrl.get(0);
				if (pageWithNewUrl != null && !pageWithNewUrl.isClosed()) {
					if (pageWithNewUrl != page) {
						log.info("New tab detected by URL diff (onPopup did not fire); saving from new tab.");
						return saveFromNewTab(pageWithNewUrl, page, downloadDir);
					}
					// Same page navigated to new URL (same-tab).
					String newUrl = pageWithNewUrl.url();
					log.info("Same-tab navigation detected by URL diff; saving from URL.");
					return saveFromUrl(newUrl, page, downloadDir);
				}
				// Fallback: any existing page with document-like URL (e.g. PDF tab
				// already
				// open; URL-based diff found 0 because that URL was already in
				// urlsBeforeClick).
				Page docPage = pagesAfter.stream()
					.filter(p -> p != null && !p.isClosed())
					.filter(p -> isDocumentLikeUrl(p.url()))
					.findFirst()
					.orElse(null);
				if (docPage != null) {
					if (docPage != page) {
						log.info("Document page found in context (e.g. PDF tab already open); saving from that tab.");
						return saveFromNewTab(docPage, page, downloadDir);
					}
					log.info("Current page is document-like; saving from URL.");
					return saveFromUrl(docPage.url(), page, downloadDir);
				}
				// Last fallback: current page URL looks like a document (no URL diff
				// caught it).
				String currentUrl = page.url();
				if (isDocumentLikeUrl(currentUrl)) {
					log.info("Current page URL looks like a document; saving from URL.");
					return saveFromUrl(currentUrl, page, downloadDir);
				}
				log.error(
						"No download event and no new tab within {} ms; link may open in same tab or require different action",
						DOWNLOAD_OR_POPUP_TIMEOUT_MS);
				return new ToolExecuteResult("Click did not trigger a download or open a new tab within "
						+ (DOWNLOAD_OR_POPUP_TIMEOUT_MS / 1000)
						+ " seconds. The link may open in the same tab or the site may use a different pattern.");
			}
			catch (ExecutionException e) {
				log.error("Error waiting for download or popup: {}", e.getCause().getMessage());
				return new ToolExecuteResult("Error waiting for download or new tab: " + e.getCause().getMessage());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return new ToolExecuteResult("Download was interrupted");
			}
			finally {
				poller.shutdown();
				try {
					poller.awaitTermination(1, TimeUnit.SECONDS);
				}
				catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
				}
			}

			if (result instanceof Download) {
				Download download = (Download) result;
				String failure = download.failure();
				if (failure != null) {
					log.warn("Download failed or was canceled: {}", failure);
					return new ToolExecuteResult("Download failed or canceled: " + failure);
				}
				String suggested = download.suggestedFilename();
				if (suggested == null || suggested.isBlank()) {
					suggested = "download";
				}
				String safeName = Paths.get(suggested).getFileName().toString();
				if (safeName == null || safeName.isBlank()) {
					safeName = "download";
				}
				Path savePath = downloadDir.resolve(safeName);
				download.saveAs(savePath);
				if (!Files.exists(savePath)) {
					return new ToolExecuteResult("Download completed but file was not saved to " + savePath);
				}
				log.info("Download saved to {}", savePath);
				return new ToolExecuteResult(
						"Downloaded file saved to " + savePath.toString() + " (filename: " + safeName + ")");
			}

			// Popup or page-diff: new tab or same-tab navigation (e.g. PDF). Fetch and
			// save.
			Page resultPage = (Page) result;
			if (resultPage == page) {
				return saveFromUrl(resultPage.url(), page, downloadDir);
			}
			return saveFromNewTab(resultPage, page, downloadDir);
		}
		catch (TimeoutError e) {
			log.error("Timeout: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser operation timed out: " + e.getMessage());
		}
		catch (PlaywrightException e) {
			log.error("Playwright error during download: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser download failed: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error during download: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser download failed: " + e.getMessage());
		}
	}

	/**
	 * Fetch a document URL using the browser context (same cookies) and save to
	 * downloadDir. Used when the link navigates in the same tab to the document URL.
	 */
	private ToolExecuteResult saveFromUrl(String resourceUrl, Page originalPage, Path downloadDir) {
		try {
			log.info("Fetching and saving document from URL: {}", resourceUrl);
			APIResponse response = originalPage.context().request().get(resourceUrl);
			if (!response.ok()) {
				return new ToolExecuteResult(
						"Failed to fetch document: HTTP " + response.status() + " " + response.statusText());
			}
			String safeName = suggestFilenameFromUrlOrHeaders(resourceUrl, response);
			Path savePath = downloadDir.resolve(safeName);
			byte[] body = response.body();
			response.dispose();
			if (body == null || body.length == 0) {
				return new ToolExecuteResult("Fetched response body was empty");
			}
			Files.write(savePath, body);
			log.info("Saved document to {}", savePath);
			return new ToolExecuteResult("File saved to " + savePath.toString() + " (filename: " + safeName + ")");
		}
		catch (Exception e) {
			log.error("Error saving from URL: {}", e.getMessage(), e);
			return new ToolExecuteResult("Failed to save file from URL: " + e.getMessage());
		}
	}

	/**
	 * Fetch the document URL from the new tab (using browser context for cookies), save
	 * to downloadDir, and close the new tab. Used when the link opens in a new tab (popup
	 * or detected by page diff).
	 */
	private ToolExecuteResult saveFromNewTab(Page newPage, Page originalPage, Path downloadDir) {
		try {
			// Wait for the new tab to load the document URL (may be about:blank briefly).
			newPage.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD,
					new Page.WaitForLoadStateOptions().setTimeout(15000));
			String resourceUrl = newPage.url();
			if (resourceUrl == null || resourceUrl.isBlank() || "about:blank".equals(resourceUrl)) {
				newPage.close();
				return new ToolExecuteResult(
						"The new tab did not load a document URL (got: " + resourceUrl + "). Cannot save.");
			}
			log.info("Link opened in new tab; fetching and saving: {}", resourceUrl);
			APIResponse response = originalPage.context().request().get(resourceUrl);
			if (!response.ok()) {
				newPage.close();
				return new ToolExecuteResult("Failed to fetch document from new tab: HTTP " + response.status() + " "
						+ response.statusText());
			}
			String safeName = suggestFilenameFromUrlOrHeaders(resourceUrl, response);
			Path savePath = downloadDir.resolve(safeName);
			byte[] body = response.body();
			response.dispose();
			if (body == null || body.length == 0) {
				newPage.close();
				return new ToolExecuteResult("Fetched response body was empty");
			}
			Files.write(savePath, body);
			newPage.close();
			if (getDriverWrapper().getCurrentPage() == newPage) {
				getDriverWrapper().setCurrentPage(originalPage);
			}
			log.info("Saved document from new tab to {}", savePath);
			return new ToolExecuteResult(
					"File opened in new tab was saved to " + savePath.toString() + " (filename: " + safeName + ")");
		}
		catch (Exception e) {
			try {
				newPage.close();
			}
			catch (Exception ignored) {
			}
			if (getDriverWrapper().getCurrentPage() == newPage) {
				getDriverWrapper().setCurrentPage(originalPage);
			}
			log.error("Error saving from new tab: {}", e.getMessage(), e);
			return new ToolExecuteResult("Failed to save file from new tab: " + e.getMessage());
		}
	}

	/**
	 * Suggest a safe filename from the response URL or Content-Disposition header.
	 */
	private static String suggestFilenameFromUrlOrHeaders(String url, APIResponse response) {
		Map<String, String> headers = response.headers();
		String contentDisposition = headers.get("content-disposition");
		if (contentDisposition != null) {
			int i = contentDisposition.toLowerCase().indexOf("filename=");
			if (i >= 0) {
				String value = contentDisposition.substring(i + 9).trim();
				if (value.startsWith("\"")) {
					int end = value.indexOf('"', 1);
					if (end > 1) {
						String name = value.substring(1, end).trim();
						if (!name.isEmpty()) {
							return Paths.get(name).getFileName().toString();
						}
					}
				}
				else if (!value.isEmpty()) {
					String name = value.split("[;\\s]")[0].trim();
					if (!name.isEmpty()) {
						return Paths.get(name).getFileName().toString();
					}
				}
			}
		}
		try {
			String path = URI.create(url).getPath();
			if (path != null && !path.isEmpty()) {
				String name = Paths.get(path).getFileName().toString();
				if (name != null && !name.isEmpty()) {
					return name;
				}
			}
		}
		catch (Exception ignored) {
		}
		return "download";
	}

	@Override
	public ToolStateInfo getCurrentToolStateString() {
		String stateString = browserUseTool.getCurrentToolStateString(getCurrentPlanId(), getRootPlanId());
		return new ToolStateInfo("bw", stateString);
	}

}
