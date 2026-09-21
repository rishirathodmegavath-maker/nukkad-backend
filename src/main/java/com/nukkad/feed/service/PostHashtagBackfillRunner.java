package com.nukkad.feed.service;

import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.PostHashtag;
import com.nukkad.feed.repository.PostHashtagRepository;
import com.nukkad.feed.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Fills {@code post_hashtags} for posts written before hashtags existed. It only does anything while the table is
 * completely empty, so it is a one-time job in practice and a cheap check on every later start. It uses the same
 * {@link Hashtags} extractor as live posts, and it can never stop the server from starting: any failure is logged
 * and skipped (Trending Topics would then simply only count newer posts).
 */
@Component
public class PostHashtagBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostHashtagBackfillRunner.class);
    private static final int BATCH = 500;

    private final PostRepository postRepository;
    private final PostHashtagRepository postHashtagRepository;

    public PostHashtagBackfillRunner(PostRepository postRepository, PostHashtagRepository postHashtagRepository) {
        this.postRepository = postRepository;
        this.postHashtagRepository = postHashtagRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int posts = backfill();
            if (posts > 0) log.info("Hashtag backfill: recorded tags for {} existing posts", posts);
        } catch (RuntimeException e) {
            log.warn("Hashtag backfill skipped: {}", e.toString());
        }
    }

    /** Returns how many posts had tags recorded. */
    int backfill() {
        if (postHashtagRepository.existsBy()) return 0;
        int recorded = 0;
        int page = 0;
        Page<Post> batch;
        do {
            batch = postRepository.findByContentContaining("#", PageRequest.of(page++, BATCH, Sort.by("id")));
            for (Post post : batch.getContent()) {
                Set<String> tags = Hashtags.extract(post.getContent());
                if (tags.isEmpty()) continue;
                List<PostHashtag> rows = tags.stream()
                        .map(tag -> PostHashtag.builder().postId(post.getId()).tag(tag).build())
                        .toList();
                postHashtagRepository.saveAll(rows);
                recorded++;
            }
        } while (batch.hasNext());
        return recorded;
    }
}
