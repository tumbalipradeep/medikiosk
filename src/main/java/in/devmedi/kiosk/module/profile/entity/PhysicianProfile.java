package in.devmedi.kiosk.module.profile.entity;

import in.devmedi.kiosk.module.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "physician_profiles")
public class PhysicianProfile {

    @Id
    private Long id;

    @MapsId
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 30)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(length = 300)
    private String qualification;

    @Column(length = 200)
    private String designation;

    @Column(length = 200)
    private String department;

    @Column(length = 300)
    private String organization;

    @Column(name = "registration_number", length = 60)
    private String registrationNumber;

    @Column(name = "preferred_language", nullable = false, length = 16)
    private String preferredLanguage = "en-IN";

    @Column(nullable = false, length = 32)
    private String theme = "system";

    @Column(name = "notification_prefs", length = 500)
    private String notificationPrefs;

    @Column(name = "profile_picture_path", length = 500)
    private String profilePicturePath;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PhysicianProfile() {
    }

    public static PhysicianProfile create(User user) {
        PhysicianProfile p = new PhysicianProfile();
        p.user = user;
        return p;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getQualification() { return qualification; }
    public void setQualification(String q) { this.qualification = q; }

    public String getDesignation() { return designation; }
    public void setDesignation(String d) { this.designation = d; }

    public String getDepartment() { return department; }
    public void setDepartment(String d) { this.department = d; }

    public String getOrganization() { return organization; }
    public void setOrganization(String o) { this.organization = o; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String r) { this.registrationNumber = r; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String l) { this.preferredLanguage = l; }

    public String getTheme() { return theme; }
    public void setTheme(String t) { this.theme = t; }

    public String getNotificationPrefs() { return notificationPrefs; }
    public void setNotificationPrefs(String p) { this.notificationPrefs = p; }

    public String getProfilePicturePath() { return profilePicturePath; }
    public void setProfilePicturePath(String p) { this.profilePicturePath = p; }
}