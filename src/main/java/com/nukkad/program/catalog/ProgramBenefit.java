package com.nukkad.program.catalog;

/** One "what you'll get" card. {@code icon} is a lucide-react icon name; {@code description} is
 *  optional — null when Admin has only given a title. {@code order} is display order within the list. */
public record ProgramBenefit(int order, String icon, String title, String description) {
}
