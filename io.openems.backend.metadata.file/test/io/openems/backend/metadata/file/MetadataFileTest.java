package io.openems.backend.metadata.file;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;

import org.junit.Test;

import io.openems.backend.common.test.DummyEventAdmin;

public class MetadataFileTest {

	private static MetadataFile createWithFileContent(String json) throws IOException, ReflectiveOperationException {
		var file = Files.createTempFile("MetadataFileTest", ".json");
		Files.writeString(file, json);

		var metadataFile = new MetadataFile();

		Field eventAdminField = MetadataFile.class.getDeclaredField("eventAdmin");
		eventAdminField.setAccessible(true);
		eventAdminField.set(metadataFile, new DummyEventAdmin(event -> {
		}));

		Field pathField = MetadataFile.class.getDeclaredField("path");
		pathField.setAccessible(true);
		pathField.set(metadataFile, file.toAbsolutePath().toString());
		return metadataFile;
	}

	@Test
	public void generateUpdateMetadataCacheNotification_mapsApikeyToEdgeId() throws Exception {
		var metadataFile = createWithFileContent("""
				{
					"edges": {
						"edge0": {
							"apikey": "apikeyA",
							"comment": "Edge A"
						},
						"edge1": {
							"apikey": "apikeyB",
							"comment": "Edge B"
						}
					}
				}
				""");

		var notification = metadataFile.generateUpdateMetadataCacheNotification();

		assertEquals(//
				Map.of("apikeyA", "edge0", "apikeyB", "edge1"), //
				notification.getApikeysToEdgeIds());
	}

}
