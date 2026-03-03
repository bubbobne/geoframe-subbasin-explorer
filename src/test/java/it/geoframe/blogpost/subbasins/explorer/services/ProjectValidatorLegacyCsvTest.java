package it.geoframe.blogpost.subbasins.explorer.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProjectValidatorLegacyCsvTest {

	@Test
	void shouldParseHortonMachineHeaderColumns() throws Exception {
		Path tmp = Files.createTempFile("legacy-hm", ".csv");
		Files.writeString(tmp, String.join("\n", "@T,table", "Created,2025-09-24 12:19", "Author,HortonMachine library",
				"@H,timestamp,value_1", "ID,,1", "Type,Date,Double", "Format,yyyy-MM-dd HH:mm,",
				",2015-08-01 01:00,116.2"));

		Method m = ProjectValidator.class.getDeclaredMethod("readCsvHeaderColumns", Path.class);
		m.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<String> cols = (List<String>) m.invoke(null, tmp);
		assertEquals(List.of("timestamp", "value_1"), cols);
	}

	@Test
	void shouldParseStandardHeaderColumns() throws Exception {
		Path tmp = Files.createTempFile("legacy-standard", ".csv");
		Files.writeString(tmp, String.join("\n", "basin_id,area,elevation", "1,10.0,1000"));

		Method m = ProjectValidator.class.getDeclaredMethod("readCsvHeaderColumns", Path.class);
		m.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<String> cols = (List<String>) m.invoke(null, tmp);
		assertEquals(List.of("basin_id", "area", "elevation"), cols);
	}
}
