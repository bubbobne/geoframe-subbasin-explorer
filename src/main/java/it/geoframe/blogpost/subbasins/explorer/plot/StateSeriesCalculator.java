package it.geoframe.blogpost.subbasins.explorer.plot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import it.geoframe.blogpost.subbasins.explorer.io.TimeseriesLoader;

public final class StateSeriesCalculator {

	public record StateColumns(String swe, String rootzoneAet, String canopyAet, String canopyFinal,
			String canopyInitial, String rootzoneFinal, String rootzoneInitial, String runoffFinal,
			String runoffInitial, String groundFinal, String groundInitial) {
	}

	public record StatePoint(long timestamp, double sweDelta, double aetDelta, double canopyDelta,
			double rootzoneDelta, double runoffDelta, double groundDelta) {
	}

	private StateSeriesCalculator() {
	}

	public static List<StatePoint> computeDeltas(List<TimeseriesLoader.TimeValueRow> rows, StateColumns columns) {
		List<StatePoint> out = new ArrayList<>();
		double previousSwe = Double.NaN;
		for (TimeseriesLoader.TimeValueRow row : rows) {
			double swe = value(row, columns.swe());
			double sweDelta = Double.isFinite(previousSwe) && Double.isFinite(swe) ? swe - previousSwe : 0d;
			if (Double.isFinite(swe)) {
				previousSwe = swe;
			}
			out.add(new StatePoint(row.timestamp(), sweDelta,
					value(row, columns.rootzoneAet()) + value(row, columns.canopyAet()),
					value(row, columns.canopyFinal()) - value(row, columns.canopyInitial()),
					value(row, columns.rootzoneFinal()) - value(row, columns.rootzoneInitial()),
					value(row, columns.runoffFinal()) - value(row, columns.runoffInitial()),
					value(row, columns.groundFinal()) - value(row, columns.groundInitial())));
		}
		return out;
	}

	public static List<StatePoint> aggregate(List<StatePoint> points, String aggregation) {
		if (points.isEmpty()) {
			return List.of();
		}
		Map<Long, StatePoint> aggregated = new LinkedHashMap<>();
		for (StatePoint p : points) {
			long keyTs = bucketStart(p.timestamp(), aggregation);
			StatePoint current = aggregated.get(keyTs);
			if (current == null) {
				aggregated.put(keyTs, new StatePoint(keyTs, p.sweDelta(), p.aetDelta(), p.canopyDelta(),
						p.rootzoneDelta(), p.runoffDelta(), p.groundDelta()));
			} else {
				aggregated.put(keyTs,
						new StatePoint(keyTs, current.sweDelta() + p.sweDelta(), current.aetDelta() + p.aetDelta(),
								current.canopyDelta() + p.canopyDelta(), current.rootzoneDelta() + p.rootzoneDelta(),
								current.runoffDelta() + p.runoffDelta(), current.groundDelta() + p.groundDelta()));
			}
		}
		return new ArrayList<>(aggregated.values());
	}

	public static long bucketStart(long ts, String aggregation) {
		Instant instant = Instant.ofEpochMilli(ts);
		if ("1h".equalsIgnoreCase(aggregation)) {
			return (ts / 3_600_000L) * 3_600_000L;
		}
		if ("12h".equalsIgnoreCase(aggregation)) {
			return (ts / 43_200_000L) * 43_200_000L;
		}
		if ("24h".equalsIgnoreCase(aggregation)) {
			return (ts / 86_400_000L) * 86_400_000L;
		}
		LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
		if ("settimana".equalsIgnoreCase(aggregation)) {
			LocalDate monday = date.with(java.time.DayOfWeek.MONDAY);
			return monday.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
		}
		if ("anno".equalsIgnoreCase(aggregation)) {
			LocalDate firstYearDay = date.with(TemporalAdjusters.firstDayOfYear());
			return firstYearDay.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
		}
		LocalDate firstMonthDay = date.with(TemporalAdjusters.firstDayOfMonth());
		return firstMonthDay.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
	}

	private static double value(TimeseriesLoader.TimeValueRow row, String key) {
		if (row == null || row.values() == null) {
			return Double.NaN;
		}
		Double v = row.values().get(key);
		return v == null ? Double.NaN : v.doubleValue();
	}
}
