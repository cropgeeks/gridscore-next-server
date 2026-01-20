package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.*;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Trial
{
	private String            name;
	private String            description;
	private Group             group;
	private List<Trait>       traits;
	private List<Comment>     comments;
	private List<Event>       events;
	private List<Person>      people;
	private Boolean           isLocked;
	private String            remoteUrl;
	private String            remoteToken;
	private Layout            layout;
	private Map<String, Cell> data;
	private String            brapiId;
	private BrapiConfig       brapiConfig;
	private SocialShareConfig socialShareConfig;
	private String            updatedOn;
	private String            createdOn;
	private String            lastSyncedOn;
	private ShareCodes        shareCodes;
	private String[]          mediaFilenameFormat;
}
