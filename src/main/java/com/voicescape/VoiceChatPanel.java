package com.voicescape;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class VoiceChatPanel extends PluginPanel
{
	private static final String SYSTEM_DEFAULT = "System Default";
	private static final String DEFAULT_SERVER_ADDRESS = "voicescape.example.com:5555";
	private static final Color  DIVIDER_COLOR  = new Color(60, 60, 60);

	private final ConfigManager configManager;

	@Setter
	private Runnable onConnect;
	@Setter
	private Runnable onDisconnect;
	@Setter
	private VoiceChatPlugin plugin;

	private final JButton connectButton = new JButton("Connect");
	private final JCheckBox autoConnectCheck = new JCheckBox("Auto-connect");
	private final JCheckBox muteAllCheck = new JCheckBox("Mute all default");

	private final JPanel playersListPanel = new JPanel(new GridBagLayout());
	private final JLabel statusLabel = new JLabel("Not connected");
	private final boolean loggedIn = false;

	// Audio devices
	private final JComboBox<String> inputDeviceCombo  = new JComboBox<>();
	private final JComboBox<String> outputDeviceCombo = new JComboBox<>();

	// Connection
	private final JTextField serverAddressField = new JTextField();

	// Voice
	private final JComboBox<VoiceMode> voiceModeCombo  = new JComboBox<>(VoiceMode.values());
	private final JTextField           pttKeyField      = new JTextField();
	private       Keybind              capturedKeybind;
	private final JPanel               pttKeyRow        = new JPanel(new BorderLayout(4, 0));
	private final JLabel               pttKeyLabel      = buildLabel("Push-to-Talk Key");
	private final JSlider              vadSlider        = new JSlider(1, 100);
	private final JPanel               vadRow           = new JPanel(new BorderLayout(4, 0));
	private final JLabel               vadLabel         = buildLabel(" Sensitivity (lower = more sensitive)");
	private final JSlider              rangeSlider      = new JSlider(1, 15);
	private final JCheckBox            mutedCheck       = new JCheckBox("Mute microphone");
	private final JCheckBox            deafenedCheck    = new JCheckBox("Deafen");

	// Audio levels
	private final JSlider micGainSlider   = new JSlider(0, 200);
	private final JSlider outputVolSlider = new JSlider(0, 200);

	// Overlay
	private final JCheckBox showOverlayCheck = new JCheckBox("Show speaker indicators");

	// Loopback
	private final JCheckBox localLoopbackCheck = new JCheckBox("Local loopback (hear your own mic)");

	public VoiceChatPanel(ConfigManager configManager, String currentInput, String currentOutput)
	{
		super(false);
		this.configManager = configManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Content panel implements Scrollable so the viewport forces it to our width,
		// preventing any component (e.g. wide JComboBox) from making the panel overflow.
		@SuppressWarnings("serial")
		class ScrollablePanel extends JPanel implements Scrollable
		{
			ScrollablePanel() { super(new GridBagLayout()); }
			@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
			@Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
			@Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 64; }
			@Override public boolean getScrollableTracksViewportWidth()  { return true; }
			@Override public boolean getScrollableTracksViewportHeight() { return false; }
		}
		JPanel content = new ScrollablePanel();
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(4, 6, 4, 6));

		GridBagConstraints c = new GridBagConstraints();
		c.fill    = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.gridx   = 0;
		c.gridy   = 0;
		c.insets  = new Insets(0, 0, 5, 0);

		content.add(buildDivider(), c); c.gridy++;
		// ── Audio Devices ────────────────────────────────────────
		content.add(buildSectionHeader("Audio Devices"), c); c.gridy++;

		content.add(buildLabel("Input Device"), c); c.gridy++;
		populateCombo(inputDeviceCombo, AudioDeviceManager.getInputDevices(), currentInput);
		inputDeviceCombo.addActionListener(e -> {
			save("inputDevice", getSelectedDevice(inputDeviceCombo));

			plugin.getCaptureThread().closeLine();
			plugin.getCaptureThread().openLine();
		});
		content.add(inputDeviceCombo, c); c.gridy++;

		content.add(buildLabel("Output Device"), c); c.gridy++;
		populateCombo(outputDeviceCombo, AudioDeviceManager.getOutputDevices(), currentOutput);
		outputDeviceCombo.addActionListener(e -> {
			save("outputDevice", getSelectedDevice(outputDeviceCombo));
			plugin.getPlaybackManager().closeLine();
			plugin.getPlaybackManager().openLine();
		});
		content.add(outputDeviceCombo, c); c.gridy++;

		content.add(buildButton("Refresh Device List", e -> refreshDevices()), c); c.gridy++;

		content.add(buildDivider(), c); c.gridy++;

		// ── Connection ───────────────────────────────────────────
		content.add(buildSectionHeader("Connection"), c); c.gridy++;

		content.add(buildLabel("Server Address (host:port)"), c); c.gridy++;
		serverAddressField.setText(cfg("serverAddress", "voicescape.example.com:5555"));
		styleTextField(serverAddressField);
		serverAddressField.addActionListener(e -> save("serverAddress", serverAddressField.getText().trim()));
		serverAddressField.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override public void focusLost(java.awt.event.FocusEvent e)
			{
				save("serverAddress", serverAddressField.getText().trim());
			}
		});
		content.add(serverAddressField, c); c.gridy++;

		JLabel defaultServerLabel = buildLabel("Default: " + DEFAULT_SERVER_ADDRESS);
		defaultServerLabel.setForeground(new Color(110, 110, 110));
		content.add(defaultServerLabel, c); c.gridy++;

		// Connect / Disconnect button (disabled until logged in)
		connectButton.setFocusPainted(false);
		connectButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		connectButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		connectButton.setEnabled(false);
		connectButton.addActionListener(e ->
		{
			String text = connectButton.getText();
			if (text.equals("Disconnect") || text.equals("Connecting..."))
			{
				connectButton.setText("Connect");
				connectButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				if (onDisconnect != null)
					onDisconnect.run();
			}
			else
			{
				connectButton.setText("Connecting...");
				connectButton.setForeground(new Color(255, 165, 0));
				if (onConnect != null)
					onConnect.run();
			}
		});
		content.add(connectButton, c); c.gridy++;

		styleCheckBox(autoConnectCheck);
		autoConnectCheck.setToolTipText("Automatically connect to the server when logging in");
		autoConnectCheck.setSelected(Boolean.parseBoolean(cfg("autoConnect", "false")));
		autoConnectCheck.addActionListener(e -> save("autoConnect", String.valueOf(autoConnectCheck.isSelected())));
		content.add(autoConnectCheck, c); c.gridy++;

		content.add(buildDivider(), c); c.gridy++;

		// ── Voice ────────────────────────────────────────────────
		content.add(buildSectionHeader("Voice"), c); c.gridy++;

		content.add(buildLabel("Voice Mode"), c); c.gridy++;
		String savedMode = cfg("voiceMode", null);
		voiceModeCombo.setSelectedItem(savedMode != null ? VoiceMode.valueOf(savedMode) : VoiceMode.PUSH_TO_TALK);
		voiceModeCombo.addActionListener(e ->
		{
			VoiceMode mode = (VoiceMode) voiceModeCombo.getSelectedItem();
			if (mode != null) { save("voiceMode", mode.name()); updateVoiceModeVisibility(mode); }
		});
		content.add(voiceModeCombo, c); c.gridy++;

		// PTT key (label tracked so we can hide/show it)
		content.add(pttKeyLabel, c); c.gridy++;
		buildPttKeyRow();
		content.add(pttKeyRow, c); c.gridy++;

		// VAD sensitivity (label tracked so we can hide/show it)
		content.add(vadLabel, c); c.gridy++;
		buildVadRow();
		content.add(vadRow, c); c.gridy++;

		content.add(buildLabel("Voice Range (tiles)"), c); c.gridy++;
		content.add(buildRangeSlider(), c); c.gridy++;

		styleCheckBox(mutedCheck);
		mutedCheck.setSelected(Boolean.parseBoolean(cfg("muted", "false")));
		mutedCheck.addActionListener(e -> save("muted", String.valueOf(mutedCheck.isSelected())));
		content.add(mutedCheck, c); c.gridy++;

		styleCheckBox(deafenedCheck);
		deafenedCheck.setSelected(Boolean.parseBoolean(cfg("deafened", "false")));
		deafenedCheck.addActionListener(e -> save("deafened", String.valueOf(deafenedCheck.isSelected())));
		content.add(deafenedCheck, c); c.gridy++;

		content.add(buildDivider(), c); c.gridy++;

		// ── Audio Levels ─────────────────────────────────────────
		content.add(buildSectionHeader("Audio Levels"), c); c.gridy++;

		content.add(buildLabel("Microphone Gain (%)"), c); c.gridy++;
		content.add(buildPercentSlider(micGainSlider, "micGain", 100), c); c.gridy++;

		content.add(buildLabel("Output Volume (%)"), c); c.gridy++;
		content.add(buildPercentSlider(outputVolSlider, "outputVolume", 100), c); c.gridy++;

		content.add(buildDivider(), c); c.gridy++;

		// ── Overlay ──────────────────────────────────────────────
		content.add(buildSectionHeader("Overlay"), c); c.gridy++;

		styleCheckBox(showOverlayCheck);
		String showOverlaySaved = cfg("showOverlay", "true");
		showOverlayCheck.setSelected(!"false".equals(showOverlaySaved));
		showOverlayCheck.addActionListener(e -> save("showOverlay", String.valueOf(showOverlayCheck.isSelected())));
		content.add(showOverlayCheck, c); c.gridy++;

		content.add(buildDivider(), c); c.gridy++;

		// ── Loopback ─────────────────────────────────────────────
		content.add(buildSectionHeader("Loopback / Testing"), c); c.gridy++;

		styleCheckBox(localLoopbackCheck);
		localLoopbackCheck.setToolTipText("Hear your own mic locally — no network involved");
		localLoopbackCheck.setSelected(Boolean.parseBoolean(cfg("localLoopback", "false")));
		localLoopbackCheck.addActionListener(e -> save("localLoopback", String.valueOf(localLoopbackCheck.isSelected())));
		content.add(localLoopbackCheck, c); c.gridy++;

		content.add(buildDivider(), c); c.gridy++;

		// ── Reset Settings ───────────────────────────────────────
		JButton resetButton = buildButton("Reset All Settings to Defaults", e -> resetAllSettings());
		resetButton.setForeground(new Color(220, 50, 50));
		content.add(resetButton, c); c.gridy++;
		content.add(buildDivider(), c); c.gridy++;


		content.add(buildSectionHeader("Mute Players"), c); c.gridy++;

		styleCheckBox(muteAllCheck);
		muteAllCheck.setToolTipText("Automatically mute all players and unmute them manually");
		muteAllCheck.setSelected(Boolean.parseBoolean(cfg("muteAll", "false")));
		muteAllCheck.addActionListener(e -> save("muteAll", String.valueOf(muteAllCheck.isSelected())));
		content.add(muteAllCheck, c); c.gridy++;

		content.add(buildDivider(), c); c.gridy++;
		playersListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(playersListPanel, c); c.gridy++;

		// Bottom spacing
		c.insets = new Insets(0, 0, 30, 0);
		content.add(new JPanel(){{ setOpaque(false); setPreferredSize(new Dimension(0, 1)); }}, c); c.gridy++;
		c.insets = new Insets(0, 0, 5, 0);

		// Filler
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		content.add(new JPanel(){{ setOpaque(false); }}, c);

		JScrollPane scroll = new JScrollPane(content,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

		// ── Status Bar (pinned at top, always visible) ───────────
		statusLabel.setForeground(new Color(180, 180, 180));
		statusLabel.setFont(FontManager.getRunescapeBoldFont());
		statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		statusLabel.setVerticalAlignment(SwingConstants.CENTER);

		JPanel statusInner = new JPanel(new BorderLayout());
		statusInner.setBackground(new Color(35, 35, 35));
		statusInner.setBorder(new CompoundBorder(
			BorderFactory.createLineBorder(DIVIDER_COLOR),
			new EmptyBorder(8, 10, 8, 10)
		));
		statusInner.add(statusLabel, BorderLayout.CENTER);
		statusInner.setPreferredSize(new Dimension(0, 80));

		JPanel statusWrapper = new JPanel(new BorderLayout());
		statusWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		statusWrapper.setBorder(new EmptyBorder(6, 6, 3, 6));
		statusWrapper.add(statusInner, BorderLayout.CENTER);

		add(statusWrapper, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);

		updateVoiceModeVisibility((VoiceMode) voiceModeCombo.getSelectedItem());
	}

	public void setConnectButtonState(boolean enabled) {
		SwingUtilities.invokeLater(() -> {
			connectButton.setEnabled(enabled);
			if(!enabled) {
				connectButton.setText("Connect");
				connectButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
	}

	public void setStatusMessage(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText(message);
			if (message.startsWith("Connected")) {
				connectButton.setForeground(new Color(255,0,0));
				connectButton.setText("Disconnect");
				statusLabel.setForeground(new Color(0, 200, 83));
			} else if (message.startsWith("Connecting")) {
				statusLabel.setForeground(new Color(255, 165, 0));
			} else {
				statusLabel.setForeground(new Color(180, 180, 180));
			}
		});
	}

	public void updatePlayerList(Map<String, String> nameToHash)
	{
		SwingUtilities.invokeLater(() ->
		{
			playersListPanel.removeAll();

			if (nameToHash == null || nameToHash.isEmpty())
			{
				JLabel empty = new JLabel("No nearby players");
				empty.setForeground(new Color(110, 110, 110));
				empty.setFont(FontManager.getRunescapeSmallFont());
				GridBagConstraints gc = new GridBagConstraints();
				gc.fill = GridBagConstraints.HORIZONTAL;
				gc.weightx = 1;
				gc.gridx = 0;
				gc.gridy = 0;
				gc.insets = new Insets(4, 4, 4, 4);
				playersListPanel.add(empty, gc);
			}
			else
			{
				AudioPlaybackManager pm = plugin != null ? plugin.getPlaybackManager() : null;
				GridBagConstraints gc = new GridBagConstraints();
				gc.fill = GridBagConstraints.HORIZONTAL;
				gc.weightx = 1;
				gc.gridx = 0;
				gc.gridy = 0;
				gc.insets = new Insets(1, 0, 1, 0);

				for (Map.Entry<String, String> entry : nameToHash.entrySet())
				{
					String name = entry.getKey();
					String hash = entry.getValue();
					boolean muted = pm != null && pm.isPlayerMuted(hash);

					JPanel row = new JPanel(new BorderLayout(4, 0));
					row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
					row.setBorder(new EmptyBorder(3, 6, 3, 4));

					JLabel nameLabel = new JLabel(name);
					nameLabel.setForeground(muted ? new Color(150, 150, 150) : Color.WHITE);
					nameLabel.setFont(FontManager.getRunescapeSmallFont());
					row.add(nameLabel, BorderLayout.CENTER);

					JButton muteBtn = new JButton(muted ? "Unmute" : "Mute");
					muteBtn.setFocusPainted(false);
					muteBtn.setPreferredSize(new Dimension(65, 20));
					muteBtn.setFont(FontManager.getRunescapeSmallFont());
					muteBtn.setBackground(muted ? ColorScheme.DARKER_GRAY_COLOR : new Color(120, 30, 30));
					muteBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
					muteBtn.addActionListener(e ->
					{
						if (pm == null) return;
						boolean nowMuted = pm.isPlayerMuted(hash);
						if (nowMuted)
						{
							pm.getUnmutedDefaultHashes().add(hash);
							pm.unmutePlayer(hash);
						}
						else
						{
							pm.getUnmutedDefaultHashes().remove(hash);
							pm.mutePlayer(hash);
						}
						// Update button in place — list refreshes on next game tick
						muteBtn.setText(nowMuted ? "Mute" : "Unmute");
						muteBtn.setBackground(nowMuted ? new Color(120, 30, 30) : ColorScheme.DARKER_GRAY_COLOR);
						nameLabel.setForeground(nowMuted ? Color.WHITE : new Color(150, 150, 150));
					});
					row.add(muteBtn, BorderLayout.EAST);

					playersListPanel.add(row, gc);
					gc.gridy++;
				}
			}

			playersListPanel.revalidate();
			playersListPanel.repaint();
		});
	}

	public void refreshDevices()
	{
		SwingUtilities.invokeLater(() ->
		{
			String curIn  = getSelectedDevice(inputDeviceCombo);
			String curOut = getSelectedDevice(outputDeviceCombo);
			populateCombo(inputDeviceCombo,  AudioDeviceManager.getInputDevices(),  curIn);
			populateCombo(outputDeviceCombo, AudioDeviceManager.getOutputDevices(), curOut);
		});
	}

	private void resetAllSettings()
	{
		save("serverAddress", DEFAULT_SERVER_ADDRESS);
		save("autoConnect", "false");
		save("voiceMode", VoiceMode.PUSH_TO_TALK.name());
		save("pushToTalkKey", KeyEvent.VK_V + ":"+KeyEvent.CTRL_DOWN_MASK);
		save("vadSensitivity", "30");
		save("voiceRange", "15");
		save("muted", "false");
		save("deafened", "false");
		save("inputDevice", "");
		save("outputDevice", "");
		save("micGain", "100");
		save("outputVolume", "100");
		save("showOverlay", "true");
		save("localLoopback", "false");

		// Update UI controls
		serverAddressField.setText(DEFAULT_SERVER_ADDRESS);
		autoConnectCheck.setSelected(false);
		voiceModeCombo.setSelectedItem(VoiceMode.PUSH_TO_TALK);
		capturedKeybind = new Keybind(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK);
		pttKeyField.setText(keybindDisplayName(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK));
		vadSlider.setValue(30);
		rangeSlider.setValue(15);
		mutedCheck.setSelected(false);
		deafenedCheck.setSelected(false);
		inputDeviceCombo.setSelectedIndex(0);
		outputDeviceCombo.setSelectedIndex(0);
		micGainSlider.setValue(100);
		outputVolSlider.setValue(100);
		showOverlayCheck.setSelected(true);
		localLoopbackCheck.setSelected(false);
		muteAllCheck.setSelected(false);
		updateVoiceModeVisibility(VoiceMode.PUSH_TO_TALK);
	}

	// ── Section builders ─────────────────────────────────────────

	private JPanel buildSectionHeader(String text)
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.BRAND_ORANGE),
			new EmptyBorder(4, 6, 4, 6)
		));
		JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		label.setFont(FontManager.getRunescapeBoldFont());
		header.add(label);
		return header;
	}

	private static JLabel buildLabel(String text)
	{
		JLabel l = new JLabel(text);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setFont(FontManager.getRunescapeSmallFont());

		return l;
	}


	private JPanel buildDivider()
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(1, 3));
		return p;
	}

	private JButton buildButton(String text, java.awt.event.ActionListener action)
	{
		JButton btn = new JButton(text);
		btn.setFocusPainted(false);
		btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		btn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		btn.addActionListener(action);
		return btn;
	}

	private void styleTextField(JTextField field)
	{
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setBorder(new CompoundBorder(
			BorderFactory.createLineBorder(DIVIDER_COLOR),
			new EmptyBorder(3, 5, 3, 5)
		));
	}

	private void styleCheckBox(JCheckBox box)
	{
		box.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.setFont(FontManager.getRunescapeSmallFont());
	}

	private void buildPttKeyRow()
	{
		pttKeyRow.setOpaque(false);
		String savedRaw = cfg("pushToTalkKey", null);
		capturedKeybind = parseKeybind(savedRaw);
		pttKeyField.setText(keybindDisplayName(capturedKeybind.getKeyCode(), capturedKeybind.getModifiers()));
		styleTextField(pttKeyField);
		pttKeyField.setEditable(false);
		pttKeyField.setFocusable(true);
		pttKeyField.setToolTipText("Click then press a key to bind");

		pttKeyField.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent e)
			{
				pttKeyField.setText("Press a key...");
				pttKeyField.requestFocusInWindow();
			}
		});
		pttKeyField.addKeyListener(new KeyAdapter()
		{
			@Override public void keyPressed(KeyEvent e)
			{
				int code = e.getKeyCode();
				if (code == KeyEvent.VK_ESCAPE)
				{
					pttKeyField.setText(keybindDisplayName(capturedKeybind.getKeyCode(), capturedKeybind.getModifiers()));
					pttKeyField.transferFocus();
					return;
				}

				if (code == KeyEvent.CTRL_DOWN_MASK || code == KeyEvent.VK_SHIFT
					|| code == KeyEvent.VK_ALT || code == KeyEvent.VK_META)
				{
					return;
				}
				int mods = e.getModifiersEx();
				capturedKeybind = new Keybind(code, mods);
				pttKeyField.setText(keybindDisplayName(code, mods));
				save("pushToTalkKey", code + ":" + mods);
				pttKeyField.transferFocus();
			}
		});
		pttKeyRow.add(pttKeyField, BorderLayout.CENTER);
	}

	private Keybind parseKeybind(String raw)
	{
		if (raw != null && raw.contains(":"))
		{
			try
			{
				String[] parts = raw.split(":");
				return new Keybind(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
			}
			catch (Exception ignored) {}
		}
		// Try as plain key code
		if (raw != null)
		{
			try
			{
				return new Keybind(Integer.parseInt(raw), 0);
			}
			catch (Exception ignored) {}
		}
		return new Keybind(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK);
	}

	private void buildVadRow()
	{
		vadRow.setOpaque(false);
		int val = parseInt(cfg("vadSensitivity", "30"), 30);
		vadSlider.setValue(val);
		vadSlider.setBackground(ColorScheme.DARK_GRAY_COLOR);
		vadSlider.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel lbl = new JLabel(val + "%");
		lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lbl.setFont(FontManager.getRunescapeSmallFont());
		lbl.setPreferredSize(new Dimension(30, 14));
		lbl.setHorizontalAlignment(SwingConstants.RIGHT);

		vadSlider.addChangeListener(e ->
		{
			lbl.setText(vadSlider.getValue() + "%");
			if (!vadSlider.getValueIsAdjusting()) save("vadSensitivity", String.valueOf(vadSlider.getValue()));
		});
		vadRow.add(vadSlider, BorderLayout.CENTER);
		vadRow.add(lbl, BorderLayout.EAST);
	}

	private JPanel buildRangeSlider()
	{
		int val = parseInt(cfg("voiceRange", "15"), 15);
		rangeSlider.setValue(val);
		rangeSlider.setBackground(ColorScheme.DARK_GRAY_COLOR);
		rangeSlider.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		rangeSlider.setPaintTicks(true);
		rangeSlider.setMinorTickSpacing(1);

		Dictionary<Integer, JLabel> labels = new Hashtable<>();
		labels.put(1,  tinyLbl("1"));
		labels.put(5, tinyLbl("5"));
		labels.put(10, tinyLbl("10"));
		labels.put(15, tinyLbl("15"));
		rangeSlider.setLabelTable(labels);
		rangeSlider.setPaintLabels(true);

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		JLabel lbl = new JLabel(val + " tiles ");
		lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lbl.setFont(FontManager.getRunescapeSmallFont());
		lbl.setPreferredSize(new Dimension(28, 14));
		lbl.setHorizontalAlignment(SwingConstants.RIGHT);

		rangeSlider.addChangeListener(e ->
		{
			lbl.setText(rangeSlider.getValue() + " tiles ");
			if (!rangeSlider.getValueIsAdjusting()) save("voiceRange", String.valueOf(rangeSlider.getValue()));
		});
		row.add(rangeSlider, BorderLayout.CENTER);
		row.add(lbl, BorderLayout.NORTH);
		return row;
	}

	private JPanel buildPercentSlider(JSlider slider, String key, int defaultVal)
	{
		int val = parseInt(cfg(key, String.valueOf(defaultVal)), defaultVal);
		slider.setValue(val);
		slider.setBackground(ColorScheme.DARK_GRAY_COLOR);
		slider.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		JLabel lbl = new JLabel(val + "%");
		lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lbl.setFont(FontManager.getRunescapeSmallFont());
		lbl.setPreferredSize(new Dimension(34, 14));
		lbl.setHorizontalAlignment(SwingConstants.RIGHT);

		slider.addChangeListener(e ->
		{
			lbl.setText(slider.getValue() + "%");
			if (!slider.getValueIsAdjusting()) save(key, String.valueOf(slider.getValue()));
		});
		row.add(slider, BorderLayout.CENTER);
		row.add(lbl, BorderLayout.EAST);
		return row;
	}

	private void updateVoiceModeVisibility(VoiceMode mode)
	{
		if (mode == null) return;
		boolean isPtt = mode == VoiceMode.PUSH_TO_TALK;
		pttKeyLabel.setVisible(isPtt);
		pttKeyRow.setVisible(isPtt);
		vadLabel.setVisible(!isPtt);
		vadRow.setVisible(!isPtt);
		SwingUtilities.invokeLater(() -> { revalidate(); repaint(); });
	}

	// ── Combo helpers ────────────────────────────────────────────

	private void populateCombo(JComboBox<String> combo, java.util.List<String> devices, String selected)
	{
		var listeners = combo.getActionListeners();
		for (var l : listeners) combo.removeActionListener(l);
		combo.removeAllItems();
		combo.addItem(SYSTEM_DEFAULT);
		for (String d : devices) combo.addItem(d);
		// Prevent combo from dictating panel width
		combo.setPrototypeDisplayValue("                              ");
		selectDevice(combo, selected);
		for (var l : listeners) combo.addActionListener(l);
	}

	private void selectDevice(JComboBox<String> combo, String name)
	{
		if (name == null || name.isEmpty()) { combo.setSelectedIndex(0); return; }
		for (int i = 0; i < combo.getItemCount(); i++)
		{
			if (combo.getItemAt(i).equals(name)) { combo.setSelectedIndex(i); return; }
		}
		combo.setSelectedIndex(0);
	}

	private String getSelectedDevice(JComboBox<String> combo)
	{
		String s = (String) combo.getSelectedItem();
		return (s == null || s.equals(SYSTEM_DEFAULT)) ? "" : s;
	}

	// ── Misc ─────────────────────────────────────────────────────

	private void save(String key, String value)
	{
		configManager.setConfiguration(VoiceChatConfig.CONFIG_GROUP, key, value);
	}

	private String cfg(String key, String fallback)
	{
		String v = configManager.getConfiguration(VoiceChatConfig.CONFIG_GROUP, key);
		return (v != null && !v.isEmpty()) ? v : fallback;
	}

	private JLabel tinyLbl(String text)
	{
		JLabel l = new JLabel(text);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private int parseInt(String s, int fallback)
	{
		try { return Integer.parseInt(s); }
		catch (NumberFormatException e) { return fallback; }
	}

	private static String keybindDisplayName(int keyCode, int modifiers)
	{
		StringBuilder sb = new StringBuilder();
		if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) sb.append("Ctrl+");
		if ((modifiers & KeyEvent.ALT_DOWN_MASK) != 0) sb.append("Alt+");
		if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) sb.append("Shift+");
		if ((modifiers & KeyEvent.META_DOWN_MASK) != 0) sb.append("Meta+");
		sb.append(KeyEvent.getKeyText(keyCode));
		return sb.toString();
	}
}
