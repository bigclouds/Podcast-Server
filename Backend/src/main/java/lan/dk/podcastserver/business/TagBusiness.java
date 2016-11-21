package lan.dk.podcastserver.business;

import javaslang.collection.HashSet;
import javaslang.collection.List;
import javaslang.collection.Set;
import lan.dk.podcastserver.entity.Tag;
import lan.dk.podcastserver.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Created by kevin on 07/06/2014.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagBusiness {

    final TagRepository tagRepository;

    public List<Tag> findAll() {
        return List.ofAll(tagRepository.findAll());
    }

    public Tag findOne(UUID id) {
        return tagRepository.findOne(id);
    }

    public Set<Tag> findByNameLike(String name) {
        return tagRepository.findByNameContainsIgnoreCase(name);
    }

    Set<Tag> getTagListByName(java.util.Set<Tag> tagList) {
        return tagList
                .stream()
                .map(t -> findByName(t.getName()))
                .collect(HashSet.collector());
    }

    private Tag findByName(String name) {
        return tagRepository
                .findByNameIgnoreCase(name)
                .getOrElse(() -> tagRepository.save(Tag.builder().name(name).build()));
    }
}
