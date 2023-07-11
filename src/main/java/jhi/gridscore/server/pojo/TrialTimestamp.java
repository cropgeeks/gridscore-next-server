package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class TrialTimestamp
{
	private String updatedOn;
	private String expiresOn;
	private Boolean showExpiryWarning = false;
}
