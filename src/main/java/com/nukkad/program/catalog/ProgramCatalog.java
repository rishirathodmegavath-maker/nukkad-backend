package com.nukkad.program.catalog;

import com.nukkad.program.entity.Program;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.nukkad.program.catalog.ProgramFieldType.DATE;
import static com.nukkad.program.catalog.ProgramFieldType.EMAIL;
import static com.nukkad.program.catalog.ProgramFieldType.MULTISELECT;
import static com.nukkad.program.catalog.ProgramFieldType.PHONE;
import static com.nukkad.program.catalog.ProgramFieldType.SELECT;
import static com.nukkad.program.catalog.ProgramFieldType.TEXT;
import static com.nukkad.program.catalog.ProgramFieldType.TEXTAREA;
import static com.nukkad.program.catalog.ProgramFieldType.URL;

/**
 * The fixed content and application question set for SPARK and IGNITE — copy, journey phases,
 * benefits, audience and application fields, all taken directly from the product spec. This is
 * deliberately code, not an admin-editable database table: BuildAdda has exactly two programs
 * today, their structure (5-phase vs 8-step journey, entirely different application questions)
 * isn't the kind of thing Admin tweaks day-to-day, and building a generic program-content CMS for
 * two fixed programs would be exactly the over-engineering the spec itself warns against. What
 * genuinely does vary operationally — whether applications are currently open, a fee, enrollment
 * info — lives in the database instead (see {@link com.nukkad.program.entity.ProgramSettings}),
 * editable by Admin. A field's {@code key} is a stable identifier: it's both the JSON key the
 * frontend posts an answer under and the column key {@code ProgramApplication} stores it under, so
 * renaming a field's label here never touches already-submitted data.
 */
public final class ProgramCatalog {

    private static final List<String> CURRENT_STATUS_OPTIONS =
            List.of("Student", "Working Professional", "Exploring Entrepreneurship", "Other");
    private static final List<String> EDUCATION_LEVEL_OPTIONS =
            List.of("High School", "Undergraduate", "Graduate", "Postgraduate", "Other");
    private static final List<String> INTEREST_OPTIONS = List.of(
            "Technology", "Healthcare", "Education", "Consumer Brands", "Fintech", "Sustainability",
            "AI / Deep Tech", "Social Impact", "Sports", "E-commerce", "Creators / Media", "Other");
    private static final List<String> YES_NO_OPTIONS = List.of("Yes", "No");
    private static final List<String> STAGE_OPTIONS =
            List.of("Idea", "Prototype", "MVP", "Early Users", "Revenue", "Scaling");

    private static final Map<Program, ProgramContent> CONTENT = new EnumMap<>(Program.class);

    static {
        CONTENT.put(Program.SPARK, spark());
        CONTENT.put(Program.IGNITE, ignite());
    }

    private ProgramCatalog() {}

    public static ProgramContent get(Program program) {
        ProgramContent content = CONTENT.get(program);
        if (content == null) throw new IllegalStateException("No catalog content for program: " + program);
        return content;
    }

    public static List<ProgramContent> all() {
        return List.copyOf(CONTENT.values());
    }

    private static ProgramContent spark() {
        return new ProgramContent(
                Program.SPARK,
                "SPARK",
                "Build your path to entrepreneurship.",
                "For people who want to explore entrepreneurship and discover what they want to build — "
                        + "learn from founders and experts, explore opportunities and industries, and take your first steps.",
                List.of(
                        "Explore entrepreneurship",
                        "Learn from founders and experts",
                        "Explore opportunities and industries",
                        "Build a builder community",
                        "Develop and validate ideas",
                        "Take first steps"),
                List.of("Students", "Working professionals", "People exploring entrepreneurship"),
                List.of(
                        new ProgramJourneyPhase(1, "Think", "Understand entrepreneurship."),
                        new ProgramJourneyPhase(2, "Explore", "Find opportunities and problems worth pursuing."),
                        new ProgramJourneyPhase(3, "Ideate", "Work on ideas and get feedback."),
                        new ProgramJourneyPhase(4, "Validate", "Test ideas with mentors and users."),
                        new ProgramJourneyPhase(5, "Build", "Take first steps toward a business.")),
                List.of(
                        "Live sessions",
                        "Founder/expert sessions",
                        "Builder community",
                        "Idea exploration",
                        "Resources/templates",
                        "Peer discussions",
                        "1:1 guidance",
                        "Demo/showcase day"),
                "Move from curiosity toward a clear business direction.",
                sparkApplicationSteps());
    }

    private static List<ProgramStep> sparkApplicationSteps() {
        return List.of(
                new ProgramStep("basic-information", "Basic Information", List.of(
                        new ProgramField("fullName", "Full Name", TEXT, true),
                        new ProgramField("email", "Email Address", EMAIL, true),
                        new ProgramField("phone", "Phone Number", PHONE, true),
                        new ProgramField("city", "City / Location", TEXT, true),
                        new ProgramField("dateOfBirth", "Date of Birth", DATE, true),
                        new ProgramField("currentStatus", "Current Status", SELECT, true, CURRENT_STATUS_OPTIONS))),
                new ProgramStep("background", "Background", List.of(
                        new ProgramField("educationLevel", "Highest Education Level", SELECT, true, EDUCATION_LEVEL_OPTIONS),
                        new ProgramField("institution", "College / School / Organization", TEXT, true),
                        new ProgramField("fieldOfStudyOrWork", "Field of Study / Work", TEXT, true),
                        new ProgramField("currentYear", "Current Year / Graduation Year", TEXT, true),
                        new ProgramField("aboutYourself", "Tell us a bit about yourself", TEXTAREA, true))),
                new ProgramStep("interests", "Interests", List.of(
                        new ProgramField("interests", "What are you interested in?", MULTISELECT, false, INTEREST_OPTIONS),
                        new ProgramField("topicsToLearn", "What topics would you like to learn more about?", TEXTAREA, false))),
                new ProgramStep("goals", "Goals", List.of(
                        new ProgramField("whyJoin", "Why do you want to join SPARK?", TEXTAREA, true),
                        new ProgramField("sixToTwelveMonthGoal", "What do you hope to achieve in the next 6–12 months?", TEXTAREA, true),
                        new ProgramField("threeToFiveYearVision", "Where do you see yourself in 3–5 years?", TEXTAREA, false))),
                new ProgramStep("short-answers", "Short Answers", List.of(
                        new ProgramField("excitesYouMost", "What excites you most about entrepreneurship?", TEXTAREA, true),
                        new ProgramField("existingIdeas", "Do you have any startup/business ideas right now?", TEXTAREA, false),
                        new ProgramField("skillsAndStrengths", "What skills or strengths do you bring?", TEXTAREA, true),
                        new ProgramField("anythingElse", "Is there anything else you'd like us to know?", TEXTAREA, false))),
                new ProgramStep("review", "Review & Submit", List.of()));
    }

    private static ProgramContent ignite() {
        return new ProgramContent(
                Program.IGNITE,
                "IGNITE",
                "Turn your idea into a real, investable business.",
                "For early-stage founders with a business idea who want to validate it, build an MVP, "
                        + "get first users, and work with mentors toward a fundable, scalable business.",
                List.of(
                        "Validate an idea",
                        "Build MVP",
                        "Get first users",
                        "Work with mentors",
                        "Learn GTM/revenue/growth",
                        "Prepare for fundraising"),
                List.of("Early-stage founders", "People with a startup idea", "Builders working toward MVP",
                        "Founders preparing for growth/fundraising"),
                List.of(
                        new ProgramJourneyPhase(1, "Idea", "Start with a real problem and opportunity."),
                        new ProgramJourneyPhase(2, "Validate", "Test with users and get evidence."),
                        new ProgramJourneyPhase(3, "Vibe Code", "Build a functional prototype using AI tools."),
                        new ProgramJourneyPhase(4, "MVP", "Launch MVP and get first users."),
                        new ProgramJourneyPhase(5, "Users", "Acquire and understand users."),
                        new ProgramJourneyPhase(6, "Business", "Build a scalable business model."),
                        new ProgramJourneyPhase(7, "Growth", "Scale with GTM, product and team."),
                        new ProgramJourneyPhase(8, "Fundraising", "Prepare and raise capital.")),
                List.of(
                        "Live sessions",
                        "Vibe coding / rapid prototyping",
                        "Industry mentor sessions",
                        "1:1 founder support",
                        "GTM & growth strategy",
                        "Resources & templates",
                        "Demo day",
                        "Exclusive community"),
                "Move from idea toward a validated, scalable, investable business.",
                igniteApplicationSteps());
    }

    private static List<ProgramStep> igniteApplicationSteps() {
        return List.of(
                new ProgramStep("basic-information", "Basic Information", List.of(
                        new ProgramField("fullName", "Full Name", TEXT, true),
                        new ProgramField("email", "Email", EMAIL, true),
                        new ProgramField("phone", "Phone", PHONE, true),
                        new ProgramField("city", "City / Location", TEXT, true),
                        new ProgramField("dateOfBirth", "Date of Birth", DATE, true))),
                new ProgramStep("background", "Background", List.of(
                        new ProgramField("educationLevel", "Highest Education", SELECT, false, EDUCATION_LEVEL_OPTIONS),
                        new ProgramField("institution", "College / Organization", TEXT, false),
                        new ProgramField("fieldOfStudyOrWork", "Field of Study / Work", TEXT, false),
                        new ProgramField("currentRole", "Current Role", TEXT, false),
                        new ProgramField("aboutYourself", "About yourself", TEXTAREA, false))),
                new ProgramStep("startup-idea", "Startup / Idea", List.of(
                        new ProgramField("whatBuilding", "What are you building?", TEXTAREA, true),
                        new ProgramField("problem", "What problem are you solving?", TEXTAREA, true),
                        new ProgramField("targetCustomer", "Who is your target customer?", TEXTAREA, true),
                        new ProgramField("demoLink", "Website/demo link", URL, false),
                        new ProgramField("hasCofounder", "Do you have a co-founder?", SELECT, false, YES_NO_OPTIONS),
                        new ProgramField("cofounderDetails", "Co-founder details (if applicable)", TEXTAREA, false))),
                new ProgramStep("progress", "Progress", List.of(
                        new ProgramField("stage", "What stage are you at?", SELECT, true, STAGE_OPTIONS),
                        new ProgramField("whatBuiltSoFar", "What have you built so far?", TEXTAREA, false),
                        new ProgramField("hasUsersOrCustomers", "Do you have users/customers?", SELECT, false, YES_NO_OPTIONS),
                        new ProgramField("approxUserCount", "Approximate number of users/customers", TEXT, false),
                        new ProgramField("validationCompleted", "What validation have you completed?", TEXTAREA, false))),
                new ProgramStep("goals", "Goals", List.of(
                        new ProgramField("programGoals", "What do you want to achieve during the program?", TEXTAREA, true),
                        new ProgramField("biggestChallenge", "What is your biggest challenge right now?", TEXTAREA, true),
                        new ProgramField("supportNeeded", "What kind of support do you need?", TEXTAREA, false),
                        new ProgramField("whySelected", "Why should you be selected?", TEXTAREA, true))),
                new ProgramStep("review", "Review & Submit", List.of()));
    }
}
