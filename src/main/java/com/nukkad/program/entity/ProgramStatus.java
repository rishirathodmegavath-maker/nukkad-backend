package com.nukkad.program.entity;

/** DRAFT is admin-only (never returned by the public {@code /api/programs} endpoints); PUBLISHED is
 *  what the public discovery page and detail page show; ARCHIVED is hidden from normal discovery but
 *  still resolvable by direct link, so an old application still has somewhere to point back to. */
public enum ProgramStatus {
    DRAFT, PUBLISHED, ARCHIVED
}
