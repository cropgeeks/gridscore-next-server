package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.*;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Person
{
	private String           id;
	private String           name;
	private String           email;
	private List<PersonType> types = new ArrayList<>();

	public static enum PersonType
	{
		DATA_COLLECTOR("data collector"),
		DATA_SUBMITTER("data submitter"),
		AUTHOR("author"),
		CORRESPONDING_AUTHOR("corresponding author"),
		QUALITY_CHECKER("quality checker");

		private String templateName;

		private PersonType(String templateName)
		{
			this.templateName = templateName;
		}

		public String getTemplateName()
		{
			return templateName;
		}
	}
}
