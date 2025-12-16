package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.*;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Cell extends CellMetadata
{
	private String                         brapiId;
	private Boolean                        isMarked;
	private Boolean                        isLocked;
	private Geography                      geography;
	private Map<String, List<Measurement>> measurements;
	private List<Comment>                  comments;
	private List<String>                   categories;

	public Cell(Cell input)
	{
		super(input);

		this.brapiId = input.getBrapiId();
		this.isMarked = input.getIsMarked();
		this.isLocked = input.getIsLocked();
		this.geography = input.getGeography();
		this.measurements = input.getMeasurements();
		this.comments = input.getComments();
		this.categories = input.getCategories();
	}
}
