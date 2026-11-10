package com.revzz.sap.readiness.service;

import com.revzz.sap.readiness.config.AppProperties;
import com.revzz.sap.readiness.domain.CapacityForecast;
import com.revzz.sap.readiness.domain.CapacitySample;
import com.revzz.sap.readiness.domain.SystemProfile;
import com.revzz.sap.readiness.repo.ContinuityRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * The capacity forecast.
 *
 * <p>A linear trend over the observed window: the average daily growth, the
 * remaining headroom, and the day the database runs out. Simple on purpose
 * — a number the application team can argue about is worth more than a
 * clever model nobody trusts.</p>
 */
@Service
public class CapacityService {

    private final ContinuityRepository repository;
    private final AppProperties properties;

    public CapacityService(ContinuityRepository repository, AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<CapacitySample> samples(String systemName) {
        return repository.findCapacity(systemName);
    }

    /**
     * The forecast for one system.
     *
     * @return the forecast, or null when there is not enough history
     */
    public CapacityForecast forecast(SystemProfile profile) {
        List<CapacitySample> samples = repository.findCapacity(profile.getName());
        if (samples.size() < 2) {
            return null;
        }

        CapacitySample first = samples.get(0);
        CapacitySample last = samples.get(samples.size() - 1);

        long days = java.time.temporal.ChronoUnit.DAYS.between(
                first.getSampleDate(), last.getSampleDate());
        if (days <= 0) {
            return null;
        }

        double growth = (double) (last.getDbSizeGb() - first.getDbSizeGb()) / days;

        CapacityForecast forecast = new CapacityForecast();
        forecast.setSystemName(profile.getName());
        forecast.setCurrentSizeGb(last.getDbSizeGb());
        forecast.setMaxSizeGb(profile.getMaxDbGb());
        forecast.setGrowthPerDayGb(Math.round(growth * 100.0) / 100.0);

        int headroom = profile.getMaxDbGb() - last.getDbSizeGb();
        if (headroom <= 0) {
            forecast.setDaysToFull(0);
            forecast.setProjectedFillDate(LocalDate.now());
            forecast.setAtRisk(true);
            return forecast;
        }
        if (growth <= 0) {
            // Not growing - there is no date to project.
            forecast.setAtRisk(false);
            return forecast;
        }

        int daysToFull = (int) Math.floor(headroom / growth);
        forecast.setDaysToFull(daysToFull);
        forecast.setProjectedFillDate(LocalDate.now().plusDays(daysToFull));
        forecast.setAtRisk(daysToFull <= properties.getPolicy().getCapacityWarningDays());
        return forecast;
    }
}
