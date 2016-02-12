package lan.dk.podcastserver.manager.worker.downloader;

import com.github.axet.wget.WGet;
import com.github.axet.wget.info.DownloadInfo;
import com.github.axet.wget.info.ex.DownloadInterruptedError;
import com.github.axet.wget.info.ex.DownloadMultipartError;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lan.dk.podcastserver.entity.Item;
import lan.dk.podcastserver.entity.Podcast;
import lan.dk.podcastserver.entity.Status;
import lan.dk.podcastserver.manager.ItemDownloadManager;
import lan.dk.podcastserver.repository.ItemRepository;
import lan.dk.podcastserver.repository.PodcastRepository;
import lan.dk.podcastserver.service.MimeTypeService;
import lan.dk.podcastserver.service.PodcastServerParameters;
import lan.dk.podcastserver.service.UrlService;
import lan.dk.podcastserver.service.factory.WGetFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import static lan.dk.podcastserver.manager.worker.downloader.HTTPDownloader.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;

/**
 * Created by kevin on 30/01/2016 for Podcast Server
 */
@RunWith(MockitoJUnitRunner.class)
public class HTTPDownloaderTest {

    public static final String ROOT_FOLDER = "/tmp/";
    public static final String TEMPORARY_EXTENSION = ".psdownload";

    @Mock UrlService urlService;
    @Mock WGetFactory wGetFactory;
    @Mock PodcastRepository podcastRepository;
    @Mock ItemRepository itemRepository;
    @Mock ItemDownloadManager itemDownloadManager;
    @Mock PodcastServerParameters podcastServerParameters;
    @Mock SimpMessagingTemplate template;
    @Mock MimeTypeService mimeTypeService;
    @InjectMocks HTTPDownloader httpDownloader;

    Podcast podcast;
    Item item;

    @Before
    public void beforeEach() {
        item = new Item()
                .setTitle("Title")
                .setUrl("http://a.fake.url/with/file.mp4?param=1")
                .setStatus(Status.NOT_DOWNLOADED);
        podcast = Podcast.builder()
                .id(12345)
                .title("A Fake Podcast")
                .items(Sets.newHashSet())
                .build()
                .add(item);

        when(podcastServerParameters.getDownloadExtension()).thenReturn(TEMPORARY_EXTENSION);
        httpDownloader.postConstruct();

        FileSystemUtils.deleteRecursively(Paths.get(ROOT_FOLDER, podcast.getTitle()).toFile());
    }

    @Test
    public void should_run_download() throws MalformedURLException {
        /* Given */
        httpDownloader.setItem(item);

        DownloadInfo downloadInfo = mock(DownloadInfo.class);
        WGet wGet = mock(WGet.class);

        when(podcastRepository.findOne(eq(podcast.getId()))).thenReturn(podcast);
        when(itemRepository.save(any(Item.class))).then(i -> i.getArguments()[0]);
        when(urlService.getRealURL(anyString())).then(i -> i.getArguments()[0]);
        when(itemDownloadManager.getRootfolder()).thenReturn(ROOT_FOLDER);
        when(wGetFactory.newDownloadInfo(anyString())).thenReturn(downloadInfo);
        when(wGetFactory.newWGet(any(DownloadInfo.class), any(File.class))).thenReturn(wGet);
        doAnswer(i -> {
            Files.createFile(Paths.get(ROOT_FOLDER, podcast.getTitle(), "file.mp4" + TEMPORARY_EXTENSION));
            item.setStatus(Status.FINISH);
            httpDownloader.finishDownload();
            return null;
        }).when(wGet).download(any(AtomicBoolean.class), any(HTTPWatcher.class));

        /* When */
        httpDownloader.run();

        /* Then */
        assertThat(item.getStatus()).isEqualTo(Status.FINISH);
        verify(podcastRepository, atLeast(1)).findOne(eq(podcast.getId()));
        verify(itemRepository, atLeast(1)).save(eq(item));
        verify(template, atLeast(1)).convertAndSend(eq(WS_TOPIC_DOWNLOAD), same(item));
        verify(template, atLeast(1)).convertAndSend(eq(String.format(WS_TOPIC_PODCAST, podcast.getId())), same(item));
        assertThat(httpDownloader.target.toString()).isEqualTo("/tmp/A Fake Podcast/file.mp4");
    }

    @Test
    public void should_stop_download() throws MalformedURLException {
        /* Given */
        httpDownloader.setItem(item);

        DownloadInfo downloadInfo = mock(DownloadInfo.class);
        WGet wGet = mock(WGet.class);

        when(podcastRepository.findOne(eq(podcast.getId()))).thenReturn(podcast);
        when(itemRepository.save(any(Item.class))).then(i -> i.getArguments()[0]);
        when(urlService.getRealURL(anyString())).then(i -> i.getArguments()[0]);
        when(itemDownloadManager.getRootfolder()).thenReturn(ROOT_FOLDER);
        when(wGetFactory.newDownloadInfo(anyString())).thenReturn(downloadInfo);
        when(wGetFactory.newWGet(any(DownloadInfo.class), any(File.class))).thenReturn(wGet);
        doAnswer(i -> Files.createFile(Paths.get(ROOT_FOLDER, podcast.getTitle(), "file.mp4" + TEMPORARY_EXTENSION)))
                .when(wGet).download(any(AtomicBoolean.class), any(HTTPWatcher.class));

        /* When */
        httpDownloader.run();
        httpDownloader.stopDownload();

        /* Then */
        assertThat(item.getStatus()).isEqualTo(Status.STOPPED);
        verify(podcastRepository, atLeast(1)).findOne(eq(podcast.getId()));
        verify(itemRepository, atLeast(1)).save(eq(item));
        verify(template, atLeast(1)).convertAndSend(eq(WS_TOPIC_DOWNLOAD), same(item));
        verify(template, atLeast(1)).convertAndSend(eq(String.format(WS_TOPIC_PODCAST, podcast.getId())), same(item));
        assertThat(httpDownloader.target.toString()).isEqualTo(String.format("/tmp/A Fake Podcast/file.mp4%s", TEMPORARY_EXTENSION));
    }

    @Test
    public void should_handle_multipart_download_error() throws MalformedURLException {
        /* Given */
        httpDownloader.setItem(item);

        DownloadInfo downloadInfo = mock(DownloadInfo.class);
        WGet wGet = mock(WGet.class);
        DownloadMultipartError error = mock(DownloadMultipartError.class);

        when(podcastRepository.findOne(eq(podcast.getId()))).thenReturn(podcast);
        when(itemRepository.save(any(Item.class))).then(i -> i.getArguments()[0]);
        when(urlService.getRealURL(anyString())).then(i -> i.getArguments()[0]);
        when(itemDownloadManager.getRootfolder()).thenReturn(ROOT_FOLDER);
        when(wGetFactory.newDownloadInfo(anyString())).thenReturn(downloadInfo);
        when(wGetFactory.newWGet(any(DownloadInfo.class), any(File.class))).thenReturn(wGet);
        when(error.getInfo()).thenReturn(downloadInfo);
        when(downloadInfo.getParts()).thenReturn(Lists.newArrayList(mock(DownloadInfo.Part.class), mock(DownloadInfo.Part.class), mock(DownloadInfo.Part.class)));
        doThrow(error).when(wGet).download(any(AtomicBoolean.class), any(HTTPWatcher.class));

        /* When */
        httpDownloader.run();

        /* Then */
        assertThat(item.getStatus()).isEqualTo(Status.STOPPED);
        verify(podcastRepository, atLeast(2)).findOne(eq(podcast.getId()));
        verify(itemRepository, atLeast(2)).save(eq(item));
        verify(template, atLeast(1)).convertAndSend(eq(WS_TOPIC_DOWNLOAD), same(item));
        verify(template, atLeast(1)).convertAndSend(eq(String.format(WS_TOPIC_PODCAST, podcast.getId())), same(item));
    }

    @Test
    public void should_handle_finish_download_without_target() {
        /* Given */
        httpDownloader.setItem(item);

        when(podcastRepository.findOne(eq(podcast.getId()))).thenReturn(podcast);
        when(itemRepository.save(any(Item.class))).then(i -> i.getArguments()[0]);

        /* When */
        httpDownloader.finishDownload();

        /* Then */
        assertThat(item.getStatus()).isEqualTo(Status.STOPPED);
        verify(podcastRepository, atLeast(1)).findOne(eq(podcast.getId()));
        verify(itemRepository, atLeast(1)).save(eq(item));
        verify(template, atLeast(1)).convertAndSend(eq(WS_TOPIC_DOWNLOAD), same(item));
        verify(template, atLeast(1)).convertAndSend(eq(String.format(WS_TOPIC_PODCAST, podcast.getId())), same(item));
        assertThat(httpDownloader.target).isNull();
    }

    @Test
    public void should_pause_a_download() {
        /* Given */
        httpDownloader.setItem(item);

        when(podcastRepository.findOne(eq(podcast.getId()))).thenReturn(podcast);
        when(itemRepository.save(any(Item.class))).then(i -> i.getArguments()[0]);

        /* When */
        httpDownloader.pauseDownload();

        /* Then */
        assertThat(item.getStatus()).isEqualTo(Status.PAUSED);
        verify(podcastRepository, atLeast(1)).findOne(eq(podcast.getId()));
        verify(itemRepository, atLeast(1)).save(eq(item));
        verify(template, atLeast(1)).convertAndSend(eq(WS_TOPIC_DOWNLOAD), same(item));
        verify(template, atLeast(1)).convertAndSend(eq(String.format(WS_TOPIC_PODCAST, podcast.getId())), same(item));
    }

    @Test
    public void should_handle_downloadunterruptedError() throws MalformedURLException {
        /* Given */
        httpDownloader.setItem(item);

        DownloadInfo downloadInfo = mock(DownloadInfo.class);
        WGet wGet = mock(WGet.class);

        when(podcastRepository.findOne(eq(podcast.getId()))).thenReturn(podcast);
        when(itemRepository.save(any(Item.class))).then(i -> i.getArguments()[0]);
        when(urlService.getRealURL(anyString())).then(i -> i.getArguments()[0]);
        when(itemDownloadManager.getRootfolder()).thenReturn(ROOT_FOLDER);
        when(wGetFactory.newDownloadInfo(anyString())).thenReturn(downloadInfo);
        when(wGetFactory.newWGet(any(DownloadInfo.class), any(File.class))).thenReturn(wGet);
        doThrow(DownloadInterruptedError.class).when(wGet).download(any(AtomicBoolean.class), any(HTTPWatcher.class));

        /* When */
        httpDownloader.run();

        /* Then */
        assertThat(item.getStatus()).isEqualTo(Status.STARTED);
        verify(podcastRepository, atLeast(1)).findOne(eq(podcast.getId()));
        verify(itemRepository, atLeast(1)).save(eq(item));
        verify(template, atLeast(1)).convertAndSend(eq(WS_TOPIC_DOWNLOAD), same(item));
        verify(template, atLeast(1)).convertAndSend(eq(String.format(WS_TOPIC_PODCAST, podcast.getId())), same(item));
    }

    @Test
    public void should_handle_IOException_during_download() throws MalformedURLException {
        /* Given */
        httpDownloader.setItem(item);

        DownloadInfo downloadInfo = mock(DownloadInfo.class);
        WGet wGet = mock(WGet.class);

        when(podcastRepository.findOne(eq(podcast.getId()))).thenReturn(podcast);
        when(itemRepository.save(any(Item.class))).then(i -> i.getArguments()[0]);
        when(urlService.getRealURL(anyString())).then(i -> i.getArguments()[0]);
        when(itemDownloadManager.getRootfolder()).thenReturn(ROOT_FOLDER);
        when(wGetFactory.newDownloadInfo(anyString())).thenReturn(downloadInfo);
        when(wGetFactory.newWGet(any(DownloadInfo.class), any(File.class))).thenReturn(wGet);
        doThrow(IOException.class).when(wGet).download(any(AtomicBoolean.class), any(HTTPWatcher.class));

        /* When */
        httpDownloader.run();

        /* Then */
        assertThat(item.getStatus()).isEqualTo(Status.STOPPED);
        verify(podcastRepository, atLeast(2)).findOne(eq(podcast.getId()));
        verify(itemRepository, atLeast(2)).save(eq(item));
        verify(template, atLeast(1)).convertAndSend(eq(WS_TOPIC_DOWNLOAD), same(item));
        verify(template, atLeast(1)).convertAndSend(eq(String.format(WS_TOPIC_PODCAST, podcast.getId())), same(item));
    }

    @Test
    public void should_save_with_same_file_already_existing() throws MalformedURLException {
        /* Given */
        httpDownloader.setItem(item);

        DownloadInfo downloadInfo = mock(DownloadInfo.class);
        WGet wGet = mock(WGet.class);

        when(podcastRepository.findOne(eq(podcast.getId()))).thenReturn(podcast);
        when(itemRepository.save(any(Item.class))).then(i -> i.getArguments()[0]);
        when(urlService.getRealURL(anyString())).then(i -> i.getArguments()[0]);
        when(itemDownloadManager.getRootfolder()).thenReturn(ROOT_FOLDER);
        when(wGetFactory.newDownloadInfo(anyString())).thenReturn(downloadInfo);
        when(wGetFactory.newWGet(any(DownloadInfo.class), any(File.class))).thenReturn(wGet);
        doAnswer(i -> {
            Files.createFile(Paths.get(ROOT_FOLDER, podcast.getTitle(), "file.mp4" + TEMPORARY_EXTENSION));
            Files.createFile(Paths.get(ROOT_FOLDER, podcast.getTitle(), "file.mp4"));
            item.setStatus(Status.FINISH);
            httpDownloader.finishDownload();
            return null;
        }).when(wGet).download(any(AtomicBoolean.class), any(HTTPWatcher.class));

        /* When */
        httpDownloader.run();

        /* Then */
        assertThat(item.getStatus()).isEqualTo(Status.FINISH);
        assertThat(item.getFileName()).isEqualTo("file.mp4");
        assertThat(httpDownloader.target.toString()).isEqualTo("/tmp/A Fake Podcast/file.mp4");
    }

    @Test
    public void should_handle_exception_during_move() throws MalformedURLException {
        /* Given */
        httpDownloader.setItem(item);

        DownloadInfo downloadInfo = mock(DownloadInfo.class);
        WGet wGet = mock(WGet.class);

        when(podcastRepository.findOne(eq(podcast.getId()))).thenReturn(podcast);
        when(itemRepository.save(any(Item.class))).then(i -> i.getArguments()[0]);
        when(urlService.getRealURL(anyString())).then(i -> i.getArguments()[0]);
        when(itemDownloadManager.getRootfolder()).thenReturn(ROOT_FOLDER);
        when(wGetFactory.newDownloadInfo(anyString())).thenReturn(downloadInfo);
        when(wGetFactory.newWGet(any(DownloadInfo.class), any(File.class))).thenReturn(wGet);
        doAnswer(i -> {
            Files.createFile(Paths.get(ROOT_FOLDER, podcast.getTitle(), "file.mp4" + TEMPORARY_EXTENSION));
            item.setStatus(Status.FINISH);
            httpDownloader.target = Paths.get("/tmp", podcast.getTitle(), "fake_file" + TEMPORARY_EXTENSION).toFile();
            httpDownloader.finishDownload();
            return null;
        }).when(wGet).download(any(AtomicBoolean.class), any(HTTPWatcher.class));

        /* When */
        httpDownloader.run();

        /* Then */
        assertThat(item.getStatus()).isEqualTo(Status.FINISH);
    }

    @Test
    public void should_get_the_same_target_file_each_call() throws MalformedURLException {
        /* Given */
        httpDownloader.setItem(item);
        when(itemDownloadManager.getRootfolder()).thenReturn(ROOT_FOLDER);

        /* When */
        httpDownloader.target = httpDownloader.getTagetFile(item);
        File target2 = httpDownloader.getTagetFile(item);

        /* Then */
        assertThat(httpDownloader.target).isSameAs(target2);
    }

    @Test
    public void should_handle_duplicate_on_file_name() throws IOException {
        /* Given */
        httpDownloader.setItem(item);
        Files.createDirectory(Paths.get(ROOT_FOLDER, podcast.getTitle()));
        Files.createFile(Paths.get(ROOT_FOLDER, podcast.getTitle(), "file.mp4" + TEMPORARY_EXTENSION));

        when(itemDownloadManager.getRootfolder()).thenReturn(ROOT_FOLDER);

        /* When */
        File targetFile = httpDownloader.getTagetFile(item);

        /* Then */
        assertThat(targetFile).isNotEqualTo(Paths.get(ROOT_FOLDER, podcast.getTitle(), "file.mp4" + TEMPORARY_EXTENSION).toFile());
    }

    @Test
    public void should_handle_error_during_creation_of_temp_file() throws IOException {
        /* Given */
        podcast.setTitle("bin");
        httpDownloader.setItem(item.setUrl("http://foo.bar.com/bash"));

        when(itemDownloadManager.getRootfolder()).thenReturn("/");
        when(podcastRepository.findOne(eq(podcast.getId()))).thenReturn(podcast);
        when(itemRepository.save(any(Item.class))).then(i -> i.getArguments()[0]);

        /* When */
        File targetFile = httpDownloader.getTagetFile(item);

        /* Then */
        assertThat(item.getStatus()).isEqualTo(Status.STOPPED);
    }

    @Test
    public void should_save_sync_with_podcast() {
        /* Given */
        httpDownloader.setItem(item);

        doThrow(RuntimeException.class).when(podcastRepository).findOne(anyInt());

        /* When */
        httpDownloader.saveSyncWithPodcast();

        /* Then */
        assertThat(httpDownloader.getItem()).isSameAs(item);
        verify(itemRepository, never()).save(any(Item.class));
    }
}