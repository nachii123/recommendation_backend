package com.example.recommendation_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "gender", length = 1, nullable = false)
    private String gender;

    @Column(name = "age", nullable = false)
    private Integer age;

    @Column(name = "occupation", nullable = false)
    private Integer occupation;

    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Rating> ratings;

    public String getAgeRangeLabel() {
        if (age == null) {
            return null;
        }
        return switch (age) {
            case 1 -> "Under 18";
            case 18 -> "18-24";
            case 25 -> "25-34";
            case 35 -> "35-44";
            case 45 -> "45-49";
            case 50 -> "50-55";
            case 56 -> "56+";
            default -> "Unknown";
        };
    }

    public String getOccupationLabel() {
        if (occupation == null) {
            return null;
        }
        return switch (occupation) {
            case 0 -> "other";
            case 1 -> "academic/educator";
            case 2 -> "artist";
            case 3 -> "clerical/admin";
            case 4 -> "college/grad student";
            case 5 -> "customer service";
            case 6 -> "doctor/health care";
            case 7 -> "executive/managerial";
            case 8 -> "farmer";
            case 9 -> "homemaker";
            case 10 -> "K-12 student";
            case 11 -> "lawyer";
            case 12 -> "programmer";
            case 13 -> "retired";
            case 14 -> "sales/marketing";
            case 15 -> "scientist";
            case 16 -> "self-employed";
            case 17 -> "technician/engineer";
            case 18 -> "tradesman/craftsman";
            case 19 -> "unemployed";
            case 20 -> "writer";
            default -> "unknown";
        };
    }
}
