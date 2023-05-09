package jhi.gridscore.server.pojo.transaction;

import jhi.gridscore.server.pojo.Trait;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.ArrayList;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class TraitContent extends ArrayList<Trait>
{
}
