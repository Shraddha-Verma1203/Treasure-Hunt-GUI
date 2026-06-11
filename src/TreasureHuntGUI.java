import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class TreasureHuntGUI {

    private static final DecimalFormat DF = new DecimalFormat("0.##");

    // ─── Treasure tables ────────────────────────────────────────────────────────
    private static final List<Item> CAVE_ITEMS = List.of(
            new Item("A1", 2, 5),  new Item("B1", 3, 7),  new Item("C1", 7, 10),
            new Item("D1", 8, 14), new Item("E1", 7, 19), new Item("F1", 2, 4),
            new Item("G1", 4, 9),  new Item("H1", 3, 10), new Item("I1", 9, 23),
            new Item("J1", 2, 2));

    private static final List<Item> MOUNTAIN_ITEMS = List.of(
            new Item("A2", 5, 15),  new Item("B2", 4, 9),  new Item("C2", 6, 19),
            new Item("D2", 9, 34),  new Item("E2", 7, 19), new Item("F2", 3, 10),
            new Item("G2", 2, 7),   new Item("H2", 1, 13), new Item("I2", 10, 29),
            new Item("J2", 12, 40));

    private static final List<Item> SEAFLOOR_ITEMS = List.of(
            new Item("A3", 3, 8),  new Item("B3", 2, 4),  new Item("C3", 2, 3),
            new Item("D3", 9, 17), new Item("E3", 11, 28), new Item("F3", 12, 30),
            new Item("G3", 7, 19), new Item("H3", 8, 21), new Item("I3", 1, 3),
            new Item("J3", 10, 29));

    // ════════════════════════════════════════════════════════════════════════════
    //  MAIN
    // ════════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new MainFrame().setVisible(true);
        });
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ════════════════════════════════════════════════════════════════════════════
    enum NodeRole { NORMAL, SOURCE, DEST_1, DEST_2, DEST_3 }

    static class GNode {
        final String origLabel;
        final int origCap;
        String label;
        int bagCap;
        NodeRole role = NodeRole.NORMAL;
        Point loc;

        GNode(String label, int cap, Point loc) {
            this.origLabel = label;
            this.label     = label;
            this.origCap   = cap;
            this.bagCap    = cap;
            this.loc       = loc;
        }
        String display() { return label; }
        @Override public String toString() { return label + " (" + bagCap + "kg)"; }
    }

    static class GEdge {
        GNode from, to;
        double weight;
        GEdge(GNode from, GNode to, double weight) {
            this.from = from; this.to = to; this.weight = weight;
        }
    }

    static class Item {
        final String name;
        final double weight, price;
        Item(String name, double weight, double price) {
            this.name = name; this.weight = weight; this.price = price;
        }
        double ratio() { return price / weight; }
    }

    static class PickedItem {
        final String name;
        final double w, p, frac;
        PickedItem(String name, double w, double p, double frac) {
            this.name = name; this.w = w; this.p = p; this.frac = frac;
        }
    }

    static class KnapsackResult {
        final List<PickedItem> items;
        final double totalW, totalP;
        KnapsackResult(List<PickedItem> items) {
            this.items = items;
            double tw = 0, tp = 0;
            for (PickedItem i : items) { tw += i.w; tp += i.p; }
            totalW = tw; totalP = tp;
        }
    }

    static class HunterResult {
        final String name, dest;
        final List<GNode> path;
        final int bagCap;
        final double dist, fuel, food, expense, itemValueRs, profit;
        final KnapsackResult ks;

        HunterResult(String name, String dest, List<GNode> path, int bagCap,
                     double dist, double fuelCostPerKm, double foodCostPerNode,
                     KnapsackResult ks) {
            this.name    = name;
            this.dest    = dest;
            this.path    = path;
            this.bagCap  = bagCap;
            this.dist    = dist;
            this.ks      = ks;
            this.fuel    = dist * fuelCostPerKm;
            // food cost charged for every node visited INCLUDING source (path includes source)
            this.food    = path.size() * foodCostPerNode;
            this.expense = fuel + food;
            this.itemValueRs = ks.totalP * 1000;
            this.profit  = itemValueRs - expense;
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  GRAPH MODEL
    // ════════════════════════════════════════════════════════════════════════════
    static class GraphModel {
        final List<GNode> nodes = new ArrayList<>();
        final List<GEdge> edges = new ArrayList<>();
        final Random rng = new Random();
        GNode source;
        final List<GNode> dests = new ArrayList<>();
        int nextIdx = 0;

        GNode addNode(int x, int y) {
            String lbl = alphaName(nextIdx++);
            int cap = 20 + rng.nextInt(6);
            GNode n = new GNode(lbl, cap, new Point(x, y));
            nodes.add(n);
            return n;
        }

        void addOrReplaceEdge(GNode from, GNode to, double w) {
            for (GEdge e : edges)
                if (e.from == from && e.to == to) { e.weight = w; return; }
            edges.add(new GEdge(from, to, w));
        }

        void removeEdge(GEdge e) { edges.remove(e); }

        void setSource(GNode n) {
            if (dests.contains(n)) throw new IllegalStateException("Node is already a destination.");
            if (source != null) { source.role = NodeRole.NORMAL; source.label = source.origLabel; source.bagCap = source.origCap; }
            source = n;
            n.role = NodeRole.SOURCE;
            n.label = "East-Blue";
            n.bagCap = 0;
        }

        void addDest(GNode n) {
            if (n == source) throw new IllegalStateException("Source cannot be a destination.");
            if (dests.contains(n)) throw new IllegalStateException("Already a destination.");
            if (dests.size() >= 3) throw new IllegalStateException("Three destinations already selected.");
            dests.add(n);
            int idx = dests.size();
            n.role   = switch (idx) { case 1 -> NodeRole.DEST_1; case 2 -> NodeRole.DEST_2; default -> NodeRole.DEST_3; };
            n.label  = switch (idx) { case 1 -> "Cave"; case 2 -> "Mountain"; default -> "Sea-Floor"; };
            n.bagCap = 0;
        }

        void resetDests() {
            for (GNode n : dests) { n.role = NodeRole.NORMAL; n.label = n.origLabel; n.bagCap = n.origCap; }
            dests.clear();
        }

        void clear() { nodes.clear(); edges.clear(); source = null; dests.clear(); nextIdx = 0; }

        GNode nodeAt(Point p, int r) {
            for (GNode n : nodes) if (p.distance(n.loc) <= r) return n;
            return null;
        }

        GEdge edgeAt(Point p) {
            for (GEdge e : edges) if (segDist(p, e.from.loc, e.to.loc) <= 8) return e;
            return null;
        }

        static double segDist(Point p, Point a, Point b) {
            double dx = b.x-a.x, dy = b.y-a.y;
            if (dx==0 && dy==0) return p.distance(a);
            double t = Math.max(0, Math.min(1, ((p.x-a.x)*dx + (p.y-a.y)*dy)/(dx*dx+dy*dy)));
            return p.distance(a.x+t*dx, a.y+t*dy);
        }

        static String alphaName(int idx) {
            StringBuilder sb = new StringBuilder();
            int v = idx;
            do { sb.insert(0,(char)('A'+(v%26))); v=v/26-1; } while(v>=0);
            return sb.toString();
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  ENGINE
    // ════════════════════════════════════════════════════════════════════════════
    static class Engine {
        static String run(GraphModel g, double fuelPer, double foodPer,
                          List<String> names, StringBuilder sb) {
            // Dijkstra from source
            Map<GNode,Double> dist = new HashMap<>();
            Map<GNode,GNode>  prev = new HashMap<>();
            for (GNode n : g.nodes) dist.put(n, Double.MAX_VALUE);
            dist.put(g.source, 0.0);
            PriorityQueue<GNode> pq = new PriorityQueue<>(Comparator.comparingDouble(dist::get));
            pq.add(g.source);
            Map<GNode,List<GEdge>> adj = new HashMap<>();
            for (GEdge e : g.edges) adj.computeIfAbsent(e.from, k->new ArrayList<>()).add(e);
            while (!pq.isEmpty()) {
                GNode u = pq.poll();
                for (GEdge e : adj.getOrDefault(u, List.of())) {
                    double nd = dist.get(u) + e.weight;
                    if (nd < dist.get(e.to)) {
                        dist.put(e.to, nd);
                        prev.put(e.to, u);
                        pq.remove(e.to); pq.add(e.to);
                    }
                }
            }

            // assign destinations randomly
            List<String> destNames = new ArrayList<>(List.of("Cave","Mountain","Sea-Floor"));
            Collections.shuffle(destNames, new Random());

            List<HunterResult> results = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String hunterName = names.get(i);
                String destName   = destNames.get(i);
                GNode destNode = g.dests.stream()
                        .filter(n -> n.display().equals(destName)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Destination node not found: " + destName));

                if (dist.get(destNode) == Double.MAX_VALUE)
                    throw new IllegalStateException("No path from East-Blue to " + destName);

                List<GNode> path = buildPath(g.source, destNode, prev);

                // bag capacity = predecessor of destination node
                GNode pred = path.size() >= 2 ? path.get(path.size()-2) : g.source;
                int bagCap = pred.bagCap;

                KnapsackResult ks = solve(destName, bagCap);
                results.add(new HunterResult(hunterName, destName, path, bagCap,
                        dist.get(destNode), fuelPer, foodPer, ks));
            }

            results.sort(Comparator.comparingDouble((HunterResult r) -> r.profit).reversed());

            // ── Build output ──
            HunterResult winner = results.get(0);
            sb.setLength(0);
            sb.append("╔══════════════════════════════════════════════╗\n");
            sb.append("  🏆 WINNER: ").append(winner.name)
                    .append("  →  ").append(winner.dest)
                    .append("  (Profit: Rs. ").append(DF.format(winner.profit)).append(")\n");
            sb.append("╚══════════════════════════════════════════════╝\n\n");

            sb.append("Destination Assignments:\n");
            for (HunterResult r : results)
                sb.append("  ").append(r.name).append("  →  ").append(r.dest).append("\n");

            sb.append("\nPath Tree (Dijkstra predecessors from East-Blue):\n");
            Set<String> seen = new LinkedHashSet<>();
            for (Map.Entry<GNode,GNode> e : prev.entrySet()) {
                String line = e.getValue().display() + " → " + e.getKey().display();
                if (seen.add(line)) sb.append("  ").append(line).append("\n");
            }

            for (HunterResult r : results) {
                sb.append("\n─────────────────────────────────────────────\n");
                sb.append("Hunter : ").append(r.name).append("  |  Destination: ").append(r.dest).append("\n");
                sb.append("Path   : ").append(formatPath(r.path)).append("\n");
                sb.append("Distance: ").append(DF.format(r.dist)).append(" km\n");
                sb.append("Magic bag capacity (from predecessor node): ").append(r.bagCap).append(" kg\n");

                sb.append("\nSelected Treasures (")
                        .append("Sea-Floor".equals(r.dest) ? "Fractional" : "0/1").append(" Knapsack):\n");
                if (r.ks.items.isEmpty()) {
                    sb.append("  (none – bag capacity is 0)\n");
                } else {
                    for (PickedItem it : r.ks.items) {
                        sb.append("  ").append(it.name)
                                .append(" | weight: ").append(DF.format(it.w)).append(" kg")
                                .append(" | price: ").append(DF.format(it.p)).append(" thousand Rs.");
                        if (it.frac < 0.9999) sb.append(" | fraction: ").append(DF.format(it.frac*100)).append("%");
                        sb.append("\n");
                    }
                }
                sb.append("Total item weight : ").append(DF.format(r.ks.totalW)).append(" kg\n");
                sb.append("Total item price  : ").append(DF.format(r.ks.totalP)).append(" thousand Rs.\n");
                sb.append("Total item value  : Rs. ").append(DF.format(r.itemValueRs)).append("\n");
                sb.append("Fuel expense      : Rs. ").append(DF.format(r.fuel))
                        .append("  (").append(DF.format(r.dist)).append(" km × Rs. ").append(DF.format(r.fuel/Math.max(r.dist,0.001))).append("/km)\n");
                sb.append("Food expense      : Rs. ").append(DF.format(r.food))
                        .append("  (").append(r.path.size()).append(" nodes × Rs. ").append(DF.format(r.food/Math.max(r.path.size(),1))).append("/node)\n");
                sb.append("Total expense     : Rs. ").append(DF.format(r.expense)).append("\n");
                sb.append("Total profit      : Rs. ").append(DF.format(r.profit)).append("\n");
            }
            return winner.name;
        }

        static List<GNode> buildPath(GNode src, GNode dst, Map<GNode,GNode> prev) {
            List<GNode> path = new ArrayList<>();
            GNode cur = dst;
            Set<GNode> vis = new HashSet<>();
            while (cur != null && vis.add(cur)) {
                path.add(cur);
                if (cur == src) { Collections.reverse(path); return path; }
                cur = prev.get(cur);
            }
            return List.of();
        }

        static KnapsackResult solve(String dest, int cap) {
            return "Sea-Floor".equals(dest) ? fractional(SEAFLOOR_ITEMS, cap)
                    : "Mountain".equals(dest)  ? zeroOne(MOUNTAIN_ITEMS, cap)
                    : zeroOne(CAVE_ITEMS, cap);
        }

        static KnapsackResult zeroOne(List<Item> items, int cap) {
            int C = Math.max(cap, 0);
            int n = items.size();
            double[][] dp   = new double[n+1][C+1];
            boolean[][] take = new boolean[n+1][C+1];
            for (int i = 1; i <= n; i++) {
                Item it = items.get(i-1);
                int w = (int) it.weight;
                for (int c = 0; c <= C; c++) {
                    dp[i][c] = dp[i-1][c];
                    if (w <= c && dp[i-1][c-w] + it.price > dp[i][c]) {
                        dp[i][c] = dp[i-1][c-w] + it.price;
                        take[i][c] = true;
                    }
                }
            }
            List<PickedItem> picked = new ArrayList<>();
            int rem = C;
            for (int i = n; i >= 1; i--) {
                if (take[i][rem]) {
                    Item it = items.get(i-1);
                    picked.add(new PickedItem(it.name, it.weight, it.price, 1.0));
                    rem -= (int) it.weight;
                }
            }
            Collections.reverse(picked);
            return new KnapsackResult(picked);
        }

        static KnapsackResult fractional(List<Item> items, int cap) {
            List<Item> sorted = new ArrayList<>(items);
            sorted.sort(Comparator.comparingDouble(Item::ratio).reversed());
            double rem = Math.max(cap, 0);
            List<PickedItem> picked = new ArrayList<>();
            for (Item it : sorted) {
                if (rem <= 0) break;
                double frac = Math.min(1.0, rem / it.weight);
                if (frac <= 0) continue;
                picked.add(new PickedItem(it.name, it.weight*frac, it.price*frac, frac));
                rem -= it.weight * frac;
            }
            return new KnapsackResult(picked);
        }

        static String formatPath(List<GNode> path) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) sb.append(" → ");
                sb.append(path.get(i).display());
            }
            return sb.toString();
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  GRAPH CANVAS
    // ════════════════════════════════════════════════════════════════════════════
    static class GraphCanvas extends JPanel {
        static final int R = 28;
        final GraphModel gm;
        GEdge selectedEdge;
        Runnable onGraphChange;

        GraphCanvas(GraphModel gm) {
            this.gm = gm;
            setBackground(new Color(245, 247, 252));
            setPreferredSize(new Dimension(860, 620));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    GNode node = gm.nodeAt(e.getPoint(), R);
                    if (node != null) { selectedEdge = null; repaint(); if (onGraphChange!=null) onGraphChange.run(); return; }
                    GEdge edge = gm.edgeAt(e.getPoint());
                    if (edge != null) { selectedEdge = edge; repaint(); return; }
                    gm.addNode(e.getX(), e.getY());
                    selectedEdge = null;
                    repaint();
                    if (onGraphChange != null) onGraphChange.run();
                }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (GEdge e : gm.edges) drawEdge(g, e);
            for (GNode n : gm.nodes) drawNode(g, n);
            g.dispose();
        }

        void drawEdge(Graphics2D g, GEdge e) {
            boolean sel = e == selectedEdge;
            g.setColor(sel ? new Color(220, 38, 38) : new Color(70, 80, 100));
            g.setStroke(new BasicStroke(sel ? 3f : 2f));
            Point a = e.from.loc, b = e.to.loc;
            g.drawLine(a.x, a.y, b.x, b.y);
            // arrow
            double angle = Math.atan2(b.y-a.y, b.x-a.x);
            int sz = 12;
            int tx = (int)(b.x - R*Math.cos(angle)), ty = (int)(b.y - R*Math.sin(angle));
            g.drawLine(tx, ty, (int)(tx - sz*Math.cos(angle-0.5)), (int)(ty - sz*Math.sin(angle-0.5)));
            g.drawLine(tx, ty, (int)(tx - sz*Math.cos(angle+0.5)), (int)(ty - sz*Math.sin(angle+0.5)));
            // weight label
            int mx = (a.x+b.x)/2, my = (a.y+b.y)/2;
            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g.drawString(DF.format(e.weight), mx+4, my-4);
        }

        void drawNode(Graphics2D g, GNode n) {
            Color fill = switch (n.role) {
                case SOURCE  -> new Color(14, 116, 144);
                case DEST_1  -> new Color(22, 163, 74);
                case DEST_2  -> new Color(202, 138, 4);
                case DEST_3  -> new Color(2, 132, 199);
                default      -> new Color(99, 102, 241);
            };
            g.setColor(fill);
            g.fillOval(n.loc.x-R, n.loc.y-R, R*2, R*2);
            g.setColor(new Color(255,255,255,80));
            g.setStroke(new BasicStroke(2));
            g.drawOval(n.loc.x-R, n.loc.y-R, R*2, R*2);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            String lbl = n.display();
            int lw = g.getFontMetrics().stringWidth(lbl);
            g.drawString(lbl, n.loc.x - lw/2, n.loc.y - 3);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            String cap = n.bagCap + "kg";
            int cw = g.getFontMetrics().stringWidth(cap);
            g.drawString(cap, n.loc.x - cw/2, n.loc.y + 13);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  MAIN FRAME
    // ════════════════════════════════════════════════════════════════════════════
    static class MainFrame extends JFrame {
        final GraphModel gm = new GraphModel();
        final GraphCanvas canvas = new GraphCanvas(gm);

        final JComboBox<GNode> cbFrom = new JComboBox<>(), cbTo = new JComboBox<>();
        final JTextField tfWeight = new JTextField("1", 5);
        final JComboBox<GNode> cbSrc = new JComboBox<>(), cbDst = new JComboBox<>();
        final JTextField tfH1 = new JTextField("Luffy", 9),
                tfH2 = new JTextField("Law", 9),
                tfH3 = new JTextField("Blackbeard", 9);
        final JTextField tfFuel = new JTextField("40", 5), tfFood = new JTextField("100", 5);
        final JTextArea  taOut  = new JTextArea();
        final JLabel     lblStatus = new JLabel("Click the canvas to add nodes.");

        MainFrame() {
            super("Treasure Hunt Simulator");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(1380, 880);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(8,8));

            add(header(), BorderLayout.NORTH);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasPanel(), controlPanel());
            split.setResizeWeight(0.64);
            add(split, BorderLayout.CENTER);
            add(statusBar(), BorderLayout.SOUTH);

            taOut.setEditable(false);
            taOut.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            taOut.setText(instructions());

            canvas.onGraphChange = this::refreshCombos;
            refreshCombos();
        }

        JComponent header() {
            JPanel p = new JPanel(new BorderLayout());
            p.setBorder(BorderFactory.createEmptyBorder(10,12,4,12));
            JLabel t = new JLabel("⚓ Treasure Hunt Simulator");
            t.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            JLabel s = new JLabel("Build a graph, pick source & 3 destinations, then simulate the race!");
            s.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            p.add(t, BorderLayout.NORTH); p.add(s, BorderLayout.SOUTH);
            return p;
        }

        JComponent canvasPanel() {
            JPanel p = new JPanel(new BorderLayout(0,6));
            p.setBorder(BorderFactory.createEmptyBorder(4,10,10,4));
            JLabel l = new JLabel("Graph Canvas  (click to add node, click edge to select it)");
            l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            p.add(l, BorderLayout.NORTH);
            p.add(canvas, BorderLayout.CENTER);
            return p;
        }

        JComponent controlPanel() {
            JPanel form = new JPanel();
            form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
            form.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));

            form.add(sectionEdge());
            form.add(Box.createVerticalStrut(8));
            form.add(sectionSource());
            form.add(Box.createVerticalStrut(8));
            form.add(sectionDest());
            form.add(Box.createVerticalStrut(8));
            form.add(sectionHunters());
            form.add(Box.createVerticalStrut(8));
            form.add(sectionCosts());
            form.add(Box.createVerticalStrut(8));
            form.add(sectionActions());

            JScrollPane formScroll = new JScrollPane(form);
            formScroll.setBorder(BorderFactory.createTitledBorder("Controls"));

            JScrollPane outScroll = new JScrollPane(taOut);
            outScroll.setBorder(BorderFactory.createTitledBorder("Simulation Results"));

            JSplitPane vSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formScroll, outScroll);
            vSplit.setResizeWeight(0.5);

            JPanel p = new JPanel(new BorderLayout());
            p.setBorder(BorderFactory.createEmptyBorder(4,4,10,10));
            p.add(vSplit);
            return p;
        }

        JPanel sectionEdge() {
            JPanel p = section("Add / Remove Directed Edge", new GridLayout(4, 2, 6, 6));
            p.add(new JLabel("From")); p.add(cbFrom);
            p.add(new JLabel("To"));   p.add(cbTo);
            p.add(new JLabel("Weight (km)")); p.add(tfWeight);
            JButton add = btn("Add Edge", this::addEdge);
            JButton rem = btn("Remove Selected Edge", this::removeEdge);
            p.add(add); p.add(rem);
            return p;
        }

        JPanel sectionSource() {
            JPanel p = section("Source Node", new GridLayout(2,1,6,6));
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
            row.add(new JLabel("Node:")); row.add(cbSrc);
            p.add(row);
            p.add(btn("Mark as East-Blue (Source)", this::setSource));
            return p;
        }

        JPanel sectionDest() {
            JPanel p = section("Destination Nodes", new GridLayout(3,1,6,6));
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
            row.add(new JLabel("Node:")); row.add(cbDst);
            p.add(row);
            p.add(btn("Add Destination (Cave / Mountain / Sea-Floor)", this::addDest));
            p.add(btn("Reset All Destinations", this::resetDests));
            return p;
        }

        JPanel sectionHunters() {
            JPanel p = section("Treasure Hunter Names", new GridLayout(3,2,6,6));
            p.add(new JLabel("Hunter 1")); p.add(tfH1);
            p.add(new JLabel("Hunter 2")); p.add(tfH2);
            p.add(new JLabel("Hunter 3")); p.add(tfH3);
            return p;
        }

        JPanel sectionCosts() {
            JPanel p = section("Travel Costs", new GridLayout(2,2,6,6));
            p.add(new JLabel("Fuel (Rs./km)")); p.add(tfFuel);
            p.add(new JLabel("Food (Rs./node)")); p.add(tfFood);
            return p;
        }

        JPanel sectionActions() {
            JPanel p = section("Simulation", new GridLayout(3,1,6,6));
            p.add(btn("▶  Run Simulation", this::runSim));
            p.add(btn("Load Demo Graph", this::loadDemo));
            p.add(btn("Clear Graph", this::clearGraph));
            return p;
        }

        JPanel section(String title, LayoutManager lm) {
            JPanel p = new JPanel(lm);
            p.setBorder(BorderFactory.createTitledBorder(title));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
            return p;
        }

        JButton btn(String text, Runnable action) {
            JButton b = new JButton(text);
            b.addActionListener(e -> action.run());
            return b;
        }

        JComponent statusBar() {
            JPanel p = new JPanel(new BorderLayout());
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1,0,0,0, new Color(210,210,210)),
                    BorderFactory.createEmptyBorder(6,12,6,12)));
            p.add(lblStatus);
            return p;
        }

        // ── Actions ─────────────────────────────────────────────────────────────
        void refreshCombos() {
            for (JComboBox<GNode> cb : List.of(cbFrom, cbTo, cbSrc, cbDst)) {
                Object sel = cb.getSelectedItem();
                DefaultComboBoxModel<GNode> m = new DefaultComboBoxModel<>();
                for (GNode n : gm.nodes) m.addElement(n);
                cb.setModel(m);
                if (sel != null && gm.nodes.contains(sel)) cb.setSelectedItem(sel);
            }
        }

        void addEdge() {
            GNode from = (GNode) cbFrom.getSelectedItem(), to = (GNode) cbTo.getSelectedItem();
            if (from == null || to == null) { err("Create nodes first."); return; }
            if (from == to) { err("From and To must be different."); return; }
            double w;
            try { w = Double.parseDouble(tfWeight.getText().trim()); if (w < 0) throw new NumberFormatException(); }
            catch (NumberFormatException ex) { err("Enter a valid positive weight."); return; }
            gm.addOrReplaceEdge(from, to, w);
            canvas.repaint();
            lblStatus.setText("Edge added: " + from.display() + " → " + to.display() + " (" + DF.format(w) + " km)");
        }

        void removeEdge() {
            GEdge e = canvas.selectedEdge;
            if (e == null) { err("Click an edge on the canvas first."); return; }
            gm.removeEdge(e);
            canvas.selectedEdge = null;
            canvas.repaint();
            lblStatus.setText("Edge removed.");
        }

        void setSource() {
            GNode n = (GNode) cbSrc.getSelectedItem();
            if (n == null) { err("Create a node first."); return; }
            try { gm.setSource(n); } catch (IllegalStateException ex) { err(ex.getMessage()); return; }
            canvas.repaint(); refreshCombos();
            lblStatus.setText("Source: " + n.display());
        }

        void addDest() {
            GNode n = (GNode) cbDst.getSelectedItem();
            if (n == null) { err("Create a node first."); return; }
            try { gm.addDest(n); } catch (IllegalStateException ex) { err(ex.getMessage()); return; }
            canvas.repaint(); refreshCombos();
            lblStatus.setText("Destination added: " + n.display());
        }

        void resetDests() {
            gm.resetDests(); canvas.repaint(); refreshCombos();
            lblStatus.setText("Destinations reset.");
        }

        void clearGraph() {
            gm.clear(); canvas.selectedEdge = null; canvas.repaint();
            refreshCombos(); taOut.setText(instructions());
            lblStatus.setText("Graph cleared.");
        }

        void loadDemo() {
            gm.clear();
            GNode a = gm.addNode(140,120), b = gm.addNode(330,90),
                    c = gm.addNode(520,150), d = gm.addNode(250,290),
                    e = gm.addNode(440,320), f = gm.addNode(650,250),
                    g = gm.addNode(760,420);
            gm.addOrReplaceEdge(a,b,5); gm.addOrReplaceEdge(a,d,7);
            gm.addOrReplaceEdge(b,c,4); gm.addOrReplaceEdge(d,e,3);
            gm.addOrReplaceEdge(e,f,4); gm.addOrReplaceEdge(c,f,6);
            gm.addOrReplaceEdge(d,c,6); gm.addOrReplaceEdge(f,g,5);
            gm.addOrReplaceEdge(c,g,9); gm.addOrReplaceEdge(e,g,6);
            gm.setSource(a);
            gm.addDest(c); gm.addDest(f); gm.addDest(g);
            refreshCombos(); canvas.repaint();
            lblStatus.setText("Demo graph loaded – press Run Simulation.");
        }

        void runSim() {
            if (gm.source == null) { err("Select a source node."); return; }
            if (gm.dests.size() != 3) { err("Select exactly 3 destination nodes."); return; }
            String n1 = tfH1.getText().trim(), n2 = tfH2.getText().trim(), n3 = tfH3.getText().trim();
            if (n1.isEmpty()||n2.isEmpty()||n3.isEmpty()) { err("Enter all 3 hunter names."); return; }
            Set<String> uniq = new HashSet<>(List.of(n1.toLowerCase(),n2.toLowerCase(),n3.toLowerCase()));
            if (uniq.size()<3) { err("Hunter names must be unique."); return; }
            double fuel, food;
            try { fuel = Double.parseDouble(tfFuel.getText().trim()); food = Double.parseDouble(tfFood.getText().trim()); }
            catch (NumberFormatException ex) { err("Enter valid fuel/food costs."); return; }
            try {
                StringBuilder sb = new StringBuilder();
                String winner = Engine.run(gm, fuel, food, List.of(n1,n2,n3), sb);
                taOut.setText(sb.toString());
                taOut.setCaretPosition(0);
                lblStatus.setText("Simulation complete  |  Winner: " + winner);
            } catch (IllegalStateException ex) {
                err(ex.getMessage());
            }
        }

        void err(String msg) {
            JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
        }

        String instructions() {
            return """
Treasure Hunt Simulator – Quick Start
======================================
1. Click anywhere on the canvas to add nodes.
   Each node gets a random bag capacity (20-25 kg) and an alphabetical name.

2. Select 'From' and 'To' nodes in the Controls panel, enter a weight,
   and click "Add Edge" to add a directed edge.
   Click on an edge on the canvas to select it, then click "Remove Selected Edge".

3. Select a node and click "Mark as East-Blue" to set the source.

4. Select nodes and click "Add Destination" three times to assign:
     1st → Cave, 2nd → Mountain, 3rd → Sea-Floor

5. Enter hunter names, fuel cost per km, and food cost per node.

6. Click "Run Simulation" to compute shortest paths (Dijkstra),
   solve knapsack problems, and determine the winner.

Knapsack algorithm:
  • Cave & Mountain → 0/1 Knapsack (items cannot be broken)
  • Sea-Floor       → Fractional Knapsack (items can be broken)

Magic bag capacity comes from the predecessor node (just before the destination)
on the shortest path from East-Blue.
""";
        }
    }
}