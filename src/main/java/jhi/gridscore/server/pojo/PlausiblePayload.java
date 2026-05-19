package jhi.gridscore.server.pojo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.google.gson.annotations.SerializedName;
import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class PlausiblePayload
{
	private String                name;
	private String                url;
	private String                domain;
	private PlausiblePayloadProps props;

	@Getter
	@Setter
	@Accessors(chain = true)
	@NoArgsConstructor
	@ToString
	public static class PlausiblePayloadProps
	{
		@JsonAlias("class")
		@SerializedName("class")
		private String clazz;
		private String message;
		private String service;
		private String location;
	}
}
