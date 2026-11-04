package com.isocode.township.ai;

public class AiUnavailableException extends RuntimeException {

    public enum Cause {
        NOT_CONFIGURED("the AI is not configured"),
        TIMEOUT("the AI did not answer within the time limit"),
        ERROR("the AI service returned an error"),
        INVALID_ANSWER("the AI gave an answer that could not be used");

        private final String explanation;

        Cause(String explanation) {
            this.explanation = explanation;
        }

        public String explanation() {
            return explanation;
        }
    }

    private final Cause cause;

    public AiUnavailableException(Cause cause, String detail) {
        super(cause.name() + ": " + detail);
        this.cause = cause;
    }

    public Cause reasonCode() {
        return cause;
    }
}
