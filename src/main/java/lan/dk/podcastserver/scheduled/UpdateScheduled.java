package lan.dk.podcastserver.scheduled;

import lan.dk.podcastserver.business.UpdatePodcastBusiness;
import lan.dk.podcastserver.manager.ItemDownloadManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Created by kevin on 26/12/2013.
 */
@Component
public class UpdateScheduled {

    private @Resource UpdatePodcastBusiness updatePodcastBusiness;
    private @Resource ItemDownloadManager IDM;

    //@Scheduled(cron="${updateAndDownload.refresh.cron}")
    @Scheduled(fixedDelay = 3600000)
    private void updateAndDownloadPodcast() {
        updatePodcastBusiness.updatePodcast();
        IDM.launchDownload();
    }
}