package it.geoframe.blogpost.subbasins.explorer.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.jfree.data.time.TimeSeries;
import org.junit.jupiter.api.Test;

import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;

class TimeseriesLoaderLegacyFolderTest {

	@Test
	void shouldLoadLegacyDischargeFromHortonMachineCsv() throws Exception {
		Path root = Files.createTempDirectory("legacy-loader");
		Path basinFolder = root.resolve("1");
		Files.createDirectories(basinFolder);
		Files.writeString(basinFolder.resolve("Q_1.csv"), String.join("\n", "@T,table", "Created,2025-09-24 12:19",
				"Author,HortonMachine library", "@H,timestamp,value_1", "ID,,1", "Type,Date,Double",
				"Format,yyyy-MM-dd HH:mm,", ",2015-08-01 01:00,116.2098", ",2015-08-01 02:00,116.0562"));

		ProjectConfig cfg = ProjectConfig.legacyFolder(root, "basin_id", "basin_id", "subbasins_complete.shp",
				"network_complete.shp", "subbasins.csv", "topology.csv", java.util.List.of("Q_"));
		TimeseriesLoader loader = new TimeseriesLoader(new TimeseriesRepository());
		TimeSeries series = new TimeSeries("legacy");
		int count = loader.fillSeriesFromLegacyFolder(cfg, "1", "Q_", series);
		assertEquals(2, count);
	}
}
