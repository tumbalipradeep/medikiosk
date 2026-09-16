package in.devmedi.kiosk.module.auth.preferences;

import in.devmedi.kiosk.module.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Server-side display preferences of one authenticated user (theme / motion /
 * text size). Shared primary key with users (same convention as the profile
 * tables). Values are closed sets validated in
 * {@link DisplayPreferencesService}; the database CHECK constraints are the
 * second line of defence. Anonymous visitors never get a row — they remain
 * localStorage-only.
 */
@Entity
@Table(name = "user_display_preferences")
public class UserDisplayPreferences {

    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 16, nullable = false)
    private String theme = "system";

    @Column(length = 16, nullable = false)
    private String motion = "dynamic";

    @Column(name = "text_size", length = 16, nullable = false)
    private String textSize = "standard";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserDisplayPreferences() {
    }

    public static UserDisplayPreferences create(User user) {
        UserDisplayPreferences preferences = new UserDisplayPreferences();
        preferences.user = user;
        preferences.updatedAt = Instant.now();
        return preferences;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getMotion() { return motion; }
    public void setMotion(String motion) { this.motion = motion; }

    public String getTextSize() { return textSize; }
    public void setTextSize(String textSize) { this.textSize = textSize; }

    public Instant getUpdatedAt() { return updatedAt; }
}
