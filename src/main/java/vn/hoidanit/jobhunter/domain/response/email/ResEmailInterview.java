package vn.hoidanit.jobhunter.domain.response.email;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ResEmailInterview {
    private long resumeId;
    private String interviewDate;
    private String interviewTime;
}
