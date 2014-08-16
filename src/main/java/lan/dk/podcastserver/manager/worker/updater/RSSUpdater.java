package lan.dk.podcastserver.manager.worker.updater;

import lan.dk.podcastserver.entity.Item;
import lan.dk.podcastserver.entity.Podcast;
import lan.dk.podcastserver.utils.DateUtils;
import lan.dk.podcastserver.utils.DigestUtils;
import lan.dk.podcastserver.utils.ImageUtils;
import lan.dk.podcastserver.utils.jDomUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintViolation;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Set;

@Component("RSSUpdater")
@Scope("prototype")
public class RSSUpdater extends AbstractUpdater {

    public Podcast updateFeed(Podcast podcast) {

        // Si l'image de présentation a changé :
        Document podcastXMLSource = null;
        try {
            podcastXMLSource = jDomUtils.jdom2Parse(podcast.getUrl());
        } catch (JDOMException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return null;
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return null;
        }

        String currentCoverURL =
                (podcastXMLSource.getRootElement().getChild("channel").getChild("image") != null)
                        ? podcastXMLSource.getRootElement().getChild("channel").getChild("image").getChildText("url")
                        : null;

        Namespace media = Namespace.getNamespace("media", "http://search.yahoo.com/mrss/");
        Namespace feedburner = Namespace.getNamespace("feedburner", "http://rssnamespace.org/feedburner/ext/1.0");

        try {
            if (podcast.getCover() == null) {
                logger.debug("Traitement de la cover général du podcast");
                podcast.setCover(ImageUtils.getCoverFromURL(new URL(currentCoverURL)));
            }

            logger.debug("Traitement des Items");
            // Parcours des éléments :
            for (Element item : podcastXMLSource.getRootElement().getChild("channel").getChildren("item")) {
                if (item.getChild("enclosure") != null || item.getChild("origEnclosureLink", feedburner) != null)   { // est un podcast utilisable
                    Item podcastItem = new Item()
                                                .setTitle(item.getChildText("title"))
                                                .setPubdate(DateUtils.rfc2822DateToTimeStamp(item.getChildText("pubDate")))
                                                .setDescription(item.getChildText("description"))
                                                .setMimeType(item.getChild("enclosure").getAttributeValue("type"))
                                                .setLength((StringUtils.isNotEmpty(item.getChild("enclosure").getAttributeValue("length")))
                                                        ? Long.parseLong(item.getChild("enclosure").getAttributeValue("length"))
                                                        : 0L);

                    if ((item.getChild("thumbnail", media) != null)) {
                        if (item.getChild("thumbnail", media).getAttributeValue("url") != null) {
                            podcastItem.setCover(ImageUtils.getCoverFromURL(new URL(item.getChild("thumbnail", media).getAttributeValue("url"))));
                        } else {
                            podcastItem.setCover(ImageUtils.getCoverFromURL(new URL(item.getChild("thumbnail", media).getText())));
                        }
                    }
                     // Gestion des cas pour l'url :
                    if (item.getChild("origEnclosureLink", feedburner) != null) {
                        podcastItem.setUrl(item.getChildText("origEnclosureLink", feedburner));
                    } else if (item.getChild("enclosure") != null) {
                        podcastItem.setUrl(item.getChild("enclosure").getAttributeValue("url"));
                    }

                    // Sauvegarde
                    if ( !podcast.getItems().contains(podcastItem)) {
                        podcastItem.setPodcast(podcast);
                        Set<ConstraintViolation<Item>> constraintViolations = validator.validate( podcastItem );
                        if (constraintViolations.isEmpty()) {
                            logger.debug("Ajout de l'épisode {}", podcastItem);
                            podcast.getItems().add(podcastItem);
                        } else {
                            logger.error(constraintViolations.toString());
                        }
                    }
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (ParseException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return podcast;
    }

    @Override
    public Podcast findPodcast(String url) {
        Podcast podcast = new Podcast();
        podcast.setUrl(url);
        Document podcastXML = null;
        try {
            podcastXML = jDomUtils.jdom2Parse(podcast.getUrl());
            if (podcastXML.getRootElement().getChild("channel").getChildText("title") != null) {
                podcast.setTitle(podcastXML.getRootElement().getChild("channel").getChildText("title"));
            }
            if (podcastXML.getRootElement().getChild("channel").getChild("image").getChildText("url") != null) {
                podcast.setCover(ImageUtils.getCoverFromURL(new URL(podcastXML.getRootElement().getChild("channel").getChild("image").getChildText("url"))));
            }
        } catch (JDOMException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return podcast;
    }

    @Override
    public String signaturePodcast(Podcast podcast) {
        return DigestUtils.generateMD5SignatureFromUrl(podcast.getUrl());
    }


}