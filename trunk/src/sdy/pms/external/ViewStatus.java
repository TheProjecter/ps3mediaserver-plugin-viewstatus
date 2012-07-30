/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 3
 * of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package sdy.pms.external;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Date;
import java.util.Queue;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import net.pms.PMS;
import net.pms.configuration.PmsConfiguration;
import net.pms.dlna.DLNAMediaInfo;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.InputFile;
import net.pms.dlna.RealFile;
import net.pms.formats.Format;
import net.pms.external.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a plugin for ps3mediaserver @see http://code.google.com/p/ps3mediaserver/
 * It allow you to keep track of which files were viewed.
 * 
 * @author Cees-Willem Hofstede <ceeswillem@gmail.com>
 * 
 */
public class ViewStatus implements StartStopListener, ThumbnailExtras, ActionListener 
{
	private static final Logger log = LoggerFactory.getLogger(ViewStatus.class);
	private boolean enabledMV;
	private JCheckBox cbEnableMV;
	Date previousDate;
	
	private Queue<Date> startDates = new LinkedList<Date>();
	
	PmsConfiguration PMSConf = PMS.getConfiguration();
	
	public ViewStatus()
	{
		if(PMSConf.getCustomProperty("enableViewStatus") == null)
		{
			// if not set in configuration, enable plugin by default
			PMSConf.setCustomProperty("enableViewStatus", true);
		}
		enabledMV = PMSConf.getCustomProperty("enableViewStatus").equals("true"); // true if plugin is enabled
	}
	
	@Override
	public void donePlaying(DLNAMediaInfo media, DLNAResource resource)
	{
		// currently only for videofiles
		if(enabledMV && resource.getType() == Format.VIDEO)
		{
			// get path information
			Path infoFilePath = Paths.get(resource.getSystemName());
			String folderName = infoFilePath.getParent().toString();
			String infoFile = folderName + "/.viewstatus";
			String infoKey = resource.getName();
			
			// create handler for properties
			Properties props = new Properties();
			
			double fileViewPercentage = 0;
			
			try {
				props.load(new FileInputStream(infoFile)); // load the viewinfo file (if any)
				fileViewPercentage = Integer.parseInt(props.getProperty(infoKey, "0"));
			} catch (IOException e) {
				log.error("viewinfo at " + infoFile + " file does not yet exist");
			}
			
			double playLengthSec = 0; // total length of the file
			
			/**
			 *  @TODO: calculation below should work without startdate.
			 *  	   Is it possible to get the exact number of seconds the file was stopped? 
			 */
				  
			playLengthSec = (int)(new Date().getTime() - startDates.poll().getTime()) / 1000;
			
			double fullLengthSec = media.getDurationInSeconds();
			
			if(fullLengthSec > 0)
			{
				double currentFileViewPercentage = (playLengthSec/fullLengthSec) * 100;
				
				// if the watched percentage is bigger than in the viewinfo file, write it to viewinfo
				if(currentFileViewPercentage > fileViewPercentage)
				{
					fileViewPercentage = Math.min(100, currentFileViewPercentage);
					props.setProperty(infoKey, Integer.toString((int)fileViewPercentage));
					
					try {
						props.store(new FileOutputStream(infoFile), null);
						
						// update the thumbnail
						media.setThumb(null);
						InputFile input = new InputFile();
						input.setFile(((RealFile) resource).getFile());
						media.generateThumbnail(input, resource.getExt(), resource.getType());
						
					} catch (IOException e) {
						logExeptionError(e);
					}
				}
			}
		}
	}

	@Override
	public void nowPlaying(DLNAMediaInfo media, DLNAResource resource) 
	{
		if(enabledMV && resource.getType() == Format.VIDEO)
		{
			startDates.add(new Date()); // set the startdate
		}
	}

	@Override
	public JComponent config() 
	{
		JPanel configPanel = new JPanel();
		cbEnableMV = new JCheckBox("enable mark viewed"); //$NON-NLS-1$
		cbEnableMV.setSelected(enabledMV);
		cbEnableMV.addActionListener(this);
		configPanel.add(cbEnableMV);
		return configPanel;
	}

	@Override
	public void shutdown() 
	{
	}

	@Override
	public String name() 
	{
		return "View Status";
	}
	
	@Override
	public void updateThumb(DLNAMediaInfo media, InputFile f) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(media.getThumb()));
			
			if (image != null) {
				Graphics g = image.getGraphics();
				Path infoFilePath = Paths.get(f.getFile().getPath());		// get path of current file
				String folderName = infoFilePath.getParent().toString();	// get folder
				String infoFile = folderName + "/.viewstatus";				// get get infofilename
				String infoKey = f.getFile().getName();						// get keyname
				
				Properties props = new Properties();
				
				try {
					props.load(new FileInputStream(infoFile));
					String viewInfo = "";
					String allViewed = props.getProperty("allviewed", "false");
					
					// if allview=true is in the infofile, mark media as viewed
					if(allViewed.equals("true"))
					{
						viewInfo = "viewed";
					}
					else 
					{
						// get viewing percentage from infofile
						int fileViewPercentage = Integer.parseInt(props.getProperty(infoKey, "0"));
						if(fileViewPercentage != 0)
						{
							viewInfo = "viewed for " + fileViewPercentage + "%";
						}
					}
					
					// if info was set, draw it on the thumbnail
					if(viewInfo != "")
					{
						// draw a senitransparent black bar to increase readability
						g.setColor(new Color(0,0,0,190));
						g.fillRect(0, image.getHeight() - 35, image.getWidth(), 35);
		
						// draw info
						g.setFont(new Font("Arial", Font.PLAIN, 25));
						g.setColor(new Color(240, 240, 240));
						FontMetrics fm = g.getFontMetrics();
						int viewInfoX = (image.getWidth() - fm.stringWidth(viewInfo)) / 2;
						int viewInfoY = image.getHeight() - 7;
						g.drawString(viewInfo, viewInfoX, viewInfoY);
						
						ByteArrayOutputStream out = new ByteArrayOutputStream();
						ImageIO.write(image, "jpeg", out);
						media.setThumb(out.toByteArray());
					}
				} catch (IOException e) {
				}
			}
		} catch (IOException e) {
			log.error("Error while updating thumbnail : " + e.getMessage());
		}
	}
	
	/**
	 * Log Exceptions by first converting it to string, and than logging that string.
	 * 
	 * @param Exeption e
	 */
	private void logExeptionError(Exception e)
	{
		StringWriter writer = new StringWriter();
	    e.printStackTrace(new PrintWriter(writer));
	    log.error(writer.toString());
	}
	
	@Override
	public void actionPerformed(ActionEvent e) 
	{
		if(e.getSource() == cbEnableMV)
		{
			enabledMV = cbEnableMV.isSelected();
			PMSConf.setCustomProperty("enableViewStatus", enabledMV);	
		}
	}
}