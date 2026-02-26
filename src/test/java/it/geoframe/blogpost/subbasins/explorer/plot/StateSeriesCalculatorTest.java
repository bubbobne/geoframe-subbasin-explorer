package it.geoframe.blogpost.subbasins.explorer.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import it.geoframe.blogpost.subbasins.explorer.io.TimeseriesLoader;

class StateSeriesCalculatorTest {

	@Test
	void computeDeltasUsesPreviousSweAndComponentDifferences() {
		StateSeriesCalculator.StateColumns c = new StateSeriesCalculator.StateColumns("swe", "rootzone_aet",
				"canopy_aet", "canopy_final", "canopy_initial", "rootzone_final", "rootzone_initial", "runoff_final",
				"runoff_initial", "ground_final", "ground_initial");

		var t1 = row(1_000L, map(10, 1, 2, 5, 3, 7, 6, 8, 2, 4, 1));
		var t2 = row(2_000L, map(12, 2, 3, 7, 4, 9, 8, 9, 4, 5, 2));

		List<StateSeriesCalculator.StatePoint> out = StateSeriesCalculator.computeDeltas(List.of(t1, t2), c);
		assertEquals(2, out.size());
		assertEquals(0d, out.get(0).sweDelta(), 1e-9);
		assertEquals(2d, out.get(1).sweDelta(), 1e-9);
		assertEquals(5d, out.get(1).aetDelta(), 1e-9);
		assertEquals(3d, out.get(1).canopyDelta(), 1e-9);
	}

	@Test
	void aggregateByMonthSumsValuesInBucket() {
		long jan1 = utcMs(2024, 1, 5);
		long jan2 = utcMs(2024, 1, 20);
		long feb1 = utcMs(2024, 2, 3);
		List<StateSeriesCalculator.StatePoint> points = List.of(
				new StateSeriesCalculator.StatePoint(jan1, 1, 2, 3, 4, 5, 6),
				new StateSeriesCalculator.StatePoint(jan2, 10, 20, 30, 40, 50, 60),
				new StateSeriesCalculator.StatePoint(feb1, 100, 200, 300, 400, 500, 600));

		List<StateSeriesCalculator.StatePoint> out = StateSeriesCalculator.aggregate(points, "mese");
		assertEquals(2, out.size());
		assertEquals(11d, out.get(0).sweDelta(), 1e-9);
		assertEquals(220d, out.get(1).aetDelta(), 1e-9);
	}

	@Test
	void bucketStartWeekStartsOnMonday() {
		long wed = utcMs(2024, 1, 10); // Wed
		long expectedMonday = utcMs(2024, 1, 8);
		assertEquals(expectedMonday, StateSeriesCalculator.bucketStart(wed, "settimana"));
	}

	private TimeseriesLoader.TimeValueRow row(long ts, Map<String, Double> values) {
		return new TimeseriesLoader.TimeValueRow(ts, values);
	}

	private Map<String, Double> map(double swe, double rootzoneAet, double canopyAet, double canopyFinal,
			double canopyInitial, double rootzoneFinal, double rootzoneInitial, double runoffFinal,
			double runoffInitial, double groundFinal, double groundInitial) {
		return Map.ofEntries(Map.entry("swe", swe), Map.entry("rootzone_aet", rootzoneAet),
				Map.entry("canopy_aet", canopyAet), Map.entry("canopy_final", canopyFinal),
				Map.entry("canopy_initial", canopyInitial), Map.entry("rootzone_final", rootzoneFinal),
				Map.entry("rootzone_initial", rootzoneInitial), Map.entry("runoff_final", runoffFinal),
				Map.entry("runoff_initial", runoffInitial), Map.entry("ground_final", groundFinal),
				Map.entry("ground_initial", groundInitial));
	}

	private long utcMs(int y, int m, int d) {
		return LocalDate.of(y, m, d).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
	}
}
