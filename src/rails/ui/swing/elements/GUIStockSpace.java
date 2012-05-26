/* $Header: /Users/blentz/rails_rcs/cvs/18xx/rails/ui/swing/elements/GUIStockSpace.java,v 1.10 2010/01/31 22:22:34 macfreek Exp $*/
package rails.ui.swing.elements;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.SwingConstants;

import org.apache.log4j.Logger;

import rails.game.PublicCompany;
import rails.game.StockSpace;
import rails.game.state.Model;
import rails.game.state.Observable;
import rails.game.state.Observer;
import rails.ui.swing.GUIToken;
import rails.util.Util;

// TODO: Check if there is something missing after replacing the ViewObject by the Observer interface 

public class GUIStockSpace extends JLayeredPane implements Observer {

    private static final long serialVersionUID = 1L;
    StockSpace model;
    JLabel priceLabel;

    int depth = 0;
    Dimension size = new Dimension(40, 40);
    List<PublicCompany> tokenList;

    private static final Color LIGHT_GRAY = new Color(200, 200, 200);
    
    private static final int TOKEN_ORIGIN_X = 12;
    private static final int TOKEN_ORIGIN_Y = 10;
    private static final int TOKEN_INCREMENT_Y = 6;
    private static final int TOKEN_DIAMETER = 20;

    protected static Logger log =
            Logger.getLogger(GUIStockSpace.class.getPackage().getName());

    public GUIStockSpace(int x, int y, StockSpace model) {

        this.model = model;

        priceLabel = new JLabel();

        priceLabel.setBounds(1, 1, size.width, size.height);
        priceLabel.setOpaque(true);

        moveToBack(priceLabel);
        setPreferredSize(new Dimension(40, 40));

        if (model != null) {

            priceLabel.setText(Integer.toString(model.getPrice()));
            //priceLabel.setBackground(stringToColor(model.getColour()));
            priceLabel.setBackground(model.getColour());
            priceLabel.setForeground(Util.isDark(priceLabel.getBackground())
                    ? Color.WHITE : Color.BLACK);
            priceLabel.setVerticalTextPosition(SwingConstants.TOP);

            model.addObserver(this);
            if (model.isStart()) {
                priceLabel.setBorder(BorderFactory.createLineBorder(Color.red,
                        2));
            }
        } else {
            priceLabel.setText("");
            priceLabel.setBackground(LIGHT_GRAY);
        }

        recreate();

    }

    private void recreate() {

        removeAll();
        add(priceLabel, new Integer(0), 0);
        placeTokens();
        // repaint();
        revalidate();
    }

    private void placeTokens() {

        if (model == null) return;
        if (model.hasTokens()) {
            tokenList = model.getTokens();

            placeToken(tokenList);
        }
    }

    private void placeToken(List<PublicCompany> tokenList) {

        int xCenter = TOKEN_ORIGIN_X;
        int yCenter = TOKEN_ORIGIN_Y;
        int diameter = TOKEN_DIAMETER;
        Point origin = new Point(16, 0);
        Dimension size = new Dimension(40, 40);
        Color bgColour;
        Color fgColour;
        PublicCompany co;
        GUIToken token;

        for (int k = tokenList.size() - 1; k >= 0; k--) {
            co = tokenList.get(k);
            bgColour = co.getBgColour();
            fgColour = co.getFgColour();

            token = new GUIToken(fgColour, bgColour, co.getId(), xCenter, yCenter, diameter);
            token.setBounds(origin.x, origin.y, size.width, size.height);

            add(token, new Integer(0), 0);
            yCenter += TOKEN_INCREMENT_Y;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see rails.ui.swing.elements.ViewObject#getModel()
     */
    public Model getModel() {
        return (Model) model;
    }

    /*
     * (non-Javadoc)
     *
     * @see rails.ui.swing.elements.ViewObject#deRegister()
     */
    public boolean deRegister() {
        if (model == null) return false;
        return model.removeObserver(this);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.rails.util.Observer#update(java.rails.util.Observable,
     * java.lang.Object)
     */
    public void update(String data) {
        recreate();
    }

    public void update() {
        // TODO Auto-generated method stub
        
    }

    public Observable getObservable() {
        // TODO Auto-generated method stub
        return null;
    }


}