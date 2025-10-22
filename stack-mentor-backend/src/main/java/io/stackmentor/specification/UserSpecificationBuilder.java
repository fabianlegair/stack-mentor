package io.stackmentor.specification;

import io.stackmentor.model.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserSpecificationBuilder {

    // Base specification -- Verified users only
    public Specification<User> isVerified() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.isTrue(root.get("isVerified"));
    }

    //Partial match for first name
    public Specification<User> firstNameContains(String firstName) {
        return (root, query, criteriaBuilder) -> {
            if (firstName == null || firstName.trim().isEmpty()) return criteriaBuilder.conjunction();
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("firstName")),
                    "%" + firstName.toLowerCase() + "%");
        };
    }

    //Partial match for last name
    public Specification<User> lastNameContains(String lastName) {
        return (root, query, criteriaBuilder) -> {
            if (lastName == null || lastName.trim().isEmpty()) return criteriaBuilder.conjunction();
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("lastName")),
                    "%" + lastName.toLowerCase() + "%");
        };
    }

    //Search by whole name or partial
    public Specification<User> nameContains(String name) {
        return (root, query, criteriaBuilder) -> {
            if (name == null || name.trim().isEmpty()) return criteriaBuilder.conjunction();

            String trimmedName = name.trim().toLowerCase();

            //Check if input contains space (first and last name)
            if (trimmedName.contains(" ")) {
                String[] parts = trimmedName.split("\\s+", 2);
                String firstNamePart = parts[0].toLowerCase();
                String lastNamePart = parts[1].toLowerCase();

                //Match for first AND last name
                return criteriaBuilder.and(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), "%" + firstNamePart + "%"),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), "%" + lastNamePart + "%")
                );
            } else {
                //Match for either first OR last name
                String pattern = "%" + trimmedName.toLowerCase() + "%";
                return criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), pattern)
                );
            }
        };
    }

    //Role match
    public Specification<User> hasRole(String role) {
        return (root, query, criteriaBuilder) -> {
            if (role == null || role.trim().isEmpty()) return criteriaBuilder.conjunction();
            return criteriaBuilder.equal(criteriaBuilder.lower(root.get("role")), role.toLowerCase());
        };
    }

    //Experience range match
    public Specification<User> experienceInRange(Integer minYears, Integer maxYears) {
        return (root, query, criteriaBuilder) -> {
            if (minYears == null && maxYears == null) return criteriaBuilder.conjunction();
            if (minYears != null && maxYears != null) {
                return criteriaBuilder.between(root.get("yearsOfExperience"), minYears, maxYears);
            }
            if (minYears != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get("yearsOfExperience"), minYears);
            }

            return criteriaBuilder.lessThanOrEqualTo(root.get("yearsOfExperience"), maxYears);
        };
    }

    //Filter by multiple industries
    public Specification<User> hasIndustries(List<String> industries) {
        return (root, query, criteriaBuilder) -> {
            if (industries == null || industries.isEmpty()) return criteriaBuilder.conjunction();

            List<String> lowerIndustries = industries.stream()
                    .map(String::toLowerCase)
                    .toList();
            return criteriaBuilder.lower(root.get("industry")).in(lowerIndustries);
        };
    }

    // Combined search specification for multiple filters
    public Specification<User> searchWithFilters(
            String searchText,
            String role,
            Integer minYears,
            Integer maxYears,
            List<String> industries) {

        Specification<User> specification = Specification.where(isVerified());

        // Add text search if provided
        specification = specification.and(nameContains(searchText));

        // Add dropdown filters
        specification = specification.and(hasRole(role))
                .and(experienceInRange(minYears, maxYears))
                .and(hasIndustries(industries));

        return specification;
    }
}
