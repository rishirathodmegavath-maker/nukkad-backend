package com.nukkad.program.catalog;

/** How an application field is entered and validated. SELECT/MULTISELECT carry their choices in
 *  {@link ProgramField#options()}; every other type is free text validated by shape (EMAIL/PHONE/
 *  DATE/URL) or just non-blank (TEXT/TEXTAREA). */
public enum ProgramFieldType {
    TEXT, TEXTAREA, EMAIL, PHONE, DATE, URL, SELECT, MULTISELECT
}
