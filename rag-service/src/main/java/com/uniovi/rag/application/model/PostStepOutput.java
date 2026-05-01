package com.uniovi.rag.application.model;

/**
 * Output of the reasoning post-step (verified or refined text, optional flag).
 */
public record PostStepOutput(String verifiedOrRefinedText, boolean verified) {

    public static PostStepOutput verified(String text) {
        return new PostStepOutput(text, true);
    }

    public static PostStepOutput refined(String text) {
        return new PostStepOutput(text, false);
    }
}
