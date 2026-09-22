package com.nukkad.startup.dto;

/** A sector some visible startup has, with how many visible startups are in it: the options of the discovery sector filter. */
public record StartupSectorDto(String sector, long count) {
}
