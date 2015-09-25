# Server Changes #

At this time you need to make some changes to the source of ps3mediaserver. This might change in the future. Basically all you have to do is to add a new pluginlistener and change !DLNAMediaInfo.java so it will call upon the plugin.

## New plugin listener ##
Create a new file ThumbnailExtras.java under net.pms.external and add the following:

```
package net.pms.external;

import net.pms.dlna.DLNAMediaInfo;
import net.pms.dlna.InputFile;

public interface ThumbnailExtras extends ExternalListener {
	public void updateThumb(DLNAMediaInfo media, InputFile f);
}
```

Then open the file !DLNAMediaInfo.java and add the following function

```
/**
 * This function calls every thumbnailExtras plugin (if any). Which will than be able
 * to add stuff to the thumbnail.
 * 
 * @param InputFile input. An InputFile is used since recource is not always available.
 */
public void thumbnailExtras(InputFile input)
{
	for(ExternalListener listener:ExternalFactory.getExternalListeners()) {
		if (listener instanceof ThumbnailExtras)
		{
			((ThumbnailExtras) listener).updateThumb(this, input);
		}
	}
}
```

Also, in the function parse(), at the end, just before

```
}
					}
				} catch (IOException e) {
					logger.debug("Error while decoding thumbnail : " + e.getMessage());
				}
			}
		}
		finalize(type, f);
		setMediaparsed(true);
	}
}
```

add this line

```
thumbnailExtras(f);
```

And that's it. Now the plugin should work. Please note, that at this time, the the thumbnail part only works if  'Use MPlayer for Video Thumbnails' is disabled. This will change soon, but keep it in mind for now.thumbnailExtras(f);
}}}```