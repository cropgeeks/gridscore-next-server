package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.*;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Cell
{
	private String                         brapiId;
	private String                         germplasm;
	private String                         rep;
	private Boolean                        isMarked;
	private Geography                      geography;
	private Map<String, List<Measurement>> measurements;
	private List<Comment>                  comments;

	public Cell(Cell input)
	{
		this.brapiId = input.getBrapiId();
		this.germplasm = input.getGermplasm();
		this.rep = input.getRep();
		this.isMarked = input.getIsMarked();
		this.geography = input.getGeography();
		this.measurements = input.getMeasurements();
		this.comments = input.getComments();
	}
}
