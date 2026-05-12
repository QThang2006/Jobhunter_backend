package vn.hoidanit.jobhunter.domain.chatGPT;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class JobSearchCriteria {
    private List<String> skills;
    private String location;
    private Double salaryMin;
    private String experienceLevel;

    private Integer postedWithinDays;
    private String jobStartDateAfter;
    private String jobEndDateBefore;

    private List<String> descriptionKeywords;
    private String companyName;
    private String sortBy;
}