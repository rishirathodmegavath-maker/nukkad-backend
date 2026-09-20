package com.nukkad.resource.controller;

import com.nukkad.admin.controller.AdminResourceController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The resource library is admin-curated: a member may only browse, open, download and save. This is a
 * structural guard — the member-facing controller must never gain a way to create, edit or delete a
 * resource, and the only place that can is the admin controller (which sits under /api/admin/**, where
 * SecurityConfig requires an admin-portal token). A build failure here means someone re-opened member
 * uploads; that is a product decision, not a refactor.
 */
class ResourceControllerReadOnlyTest {

    private static String[] paths(Method m) {
        PostMapping post = m.getAnnotation(PostMapping.class);
        return post == null ? new String[0] : post.value();
    }

    @Test
    void theMemberControllerHasNoUpdateOrDeleteEndpoints() {
        for (Method m : ResourceController.class.getDeclaredMethods()) {
            assertThat(m.isAnnotationPresent(PutMapping.class)).as("PUT on " + m.getName()).isFalse();
            assertThat(m.isAnnotationPresent(DeleteMapping.class)).as("DELETE on " + m.getName()).isFalse();
            assertThat(m.isAnnotationPresent(PatchMapping.class)).as("PATCH on " + m.getName()).isFalse();
            assertThat(m.isAnnotationPresent(RequestMapping.class)).as("generic mapping on " + m.getName()).isFalse();
        }
    }

    @Test
    void theOnlyMemberPostIsTheSaveBookmarkToggle() {
        List<String> postPaths = Arrays.stream(ResourceController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PostMapping.class))
                .flatMap(m -> Arrays.stream(paths(m)).map(p -> p.isEmpty() ? "<root>" : p))
                .toList();

        assertThat(postPaths).containsExactly("/{id}/save");
    }

    @Test
    void createUpdateAndDeleteLiveOnlyUnderTheAdminPath() {
        String base = AdminResourceController.class.getAnnotation(RequestMapping.class).value()[0];
        assertThat(base).isEqualTo("/api/admin/resources");

        Method[] admin = AdminResourceController.class.getDeclaredMethods();
        assertThat(Arrays.stream(admin).anyMatch(m -> m.isAnnotationPresent(PostMapping.class))).as("admin create").isTrue();
        assertThat(Arrays.stream(admin).anyMatch(m -> m.isAnnotationPresent(PutMapping.class))).as("admin update").isTrue();
        assertThat(Arrays.stream(admin).anyMatch(m -> m.isAnnotationPresent(DeleteMapping.class))).as("admin delete").isTrue();
    }
}
