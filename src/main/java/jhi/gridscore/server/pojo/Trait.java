package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Trait
{
	private String       id;
	private String       brapiId;
	private String       name;
	private String       description;
	private String       dataType;
	private boolean      allowRepeats = false;
	private int          setSize      = 1;
	private Group        group;
	private Restrictions restrictions;
	private Timeframe    timeframe;
	private boolean      hasImage     = false;
	private String       imageUrl     = null;
}
