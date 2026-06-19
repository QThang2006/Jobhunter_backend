package vn.hoidanit.jobhunter.domain.response.email;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailValidationResponse {

    @JsonProperty("email_deliverability")
    private Deliverability emailDeliverability;

    public Deliverability getEmailDeliverability() { return emailDeliverability; }
    public void setEmailDeliverability(Deliverability emailDeliverability) { this.emailDeliverability = emailDeliverability; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Deliverability {

        @JsonProperty("is_smtp_valid")
        private boolean isSmtpValid;

        public boolean isSmtpValid() { return isSmtpValid; }
        public void setSmtpValid(boolean smtpValid) { this.isSmtpValid = smtpValid; }
    }
}