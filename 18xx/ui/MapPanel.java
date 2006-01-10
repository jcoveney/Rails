/* $Header: /Users/blentz/rails_rcs/cvs/18xx/ui/Attic/MapPanel.java,v 1.1 2006/01/10 20:07:17 wakko666 Exp $
 * 
 * Created on 08-Aug-2005
 * Change Log:
 */
package ui;

import game.*;
import game.special.SpecialTileLay;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import ui.hexmap.*;

/**
 * MapWindow class displays the Map Window. It's shocking, I know.
 * 
 * @author Erik Vos
 * @author Brett
 */
public class MapPanel extends JPanel // implements WindowListener
{

	private MapManager mmgr;
	private HexMap map;
	private JScrollPane scrollPane;
	private UpgradesPanel upgradePanel;

	public MapPanel()
	{
		Scale.set(15);

		setLayout(new BorderLayout());

		mmgr = MapManager.getInstance();
		try
		{
			map = (HexMap) Class.forName(mmgr.getMapUIClassName())
					.newInstance();
		}
		catch (Exception e)
		{
			System.out.println("Map class instantiation error:\n");
			e.printStackTrace();
			return;
		}

		map.addMouseListener(map);
		map.addMouseMotionListener(map);
		ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);

		scrollPane = new JScrollPane(map);
		scrollPane.setSize(map.getPreferredSize());

		add(scrollPane, BorderLayout.CENTER);

		setSize(map.getPreferredSize().width + 100,
				map.getPreferredSize().height + 40);
		setLocation(25, 25);
	}

	public void setSpecialTileLays(java.util.List specials)
	{
		map.setSpecials(specials);
	}
	
	public void enableBaseTokenLaying(boolean enabled)
	{
		map.enableBaseTokenLaying(enabled);
		upgradePanel.initBaseTokenLaying(enabled);
	}
	
	public void enableTileLaying(boolean enabled)
	{
		map.enableTileLaying(enabled);
		upgradePanel.initTileLaying(enabled);
	}

	public void keyPressed(KeyEvent e)
	{
		if (e.getKeyCode() == KeyEvent.VK_F1)
		{
			HelpWindow.displayHelp(GameManager.getInstance().getHelp());
			e.consume();
		}
	}

	public void keyReleased(KeyEvent e)
	{
	}

	public HexMap getMap()
	{
		return map;
	}

	
	public void setUpgradesPanel(UpgradesPanel upgradePanel)
	{
		this.upgradePanel = upgradePanel;
	}
}
