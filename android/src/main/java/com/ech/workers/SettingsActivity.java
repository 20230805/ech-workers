package com.ech.workers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private Spinner profileSpinner;
    
    // 配置项视图
    private LinearLayout itemServerAddr;
    private LinearLayout itemServerIp;
    private LinearLayout itemDnsServer;
    private LinearLayout itemEchDomain;
    private LinearLayout itemToken;
    private LinearLayout itemRoutingMode;
    private LinearLayout itemFallbackHosts;
    
    // 配置值显示
    private TextView valueServerAddr;
    private TextView valueServerIp;
    private TextView valueDnsServer;
    private TextView valueEchDomain;
    private TextView valueToken;
    private TextView valueRoutingMode;
    private TextView valueFallbackHosts;
    
    private ArrayList<String> profiles;
    private String currentProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        prefs = getSharedPreferences("profiles", MODE_PRIVATE);
        
        initViews();
        loadProfiles();
        setupListeners();
    }

    private void initViews() {
        profileSpinner = findViewById(R.id.profile_spinner);
        
        itemServerAddr = findViewById(R.id.item_server_addr);
        itemServerIp = findViewById(R.id.item_server_ip);
        itemDnsServer = findViewById(R.id.item_dns_server);
        itemEchDomain = findViewById(R.id.item_ech_domain);
        itemToken = findViewById(R.id.item_token);
        itemRoutingMode = findViewById(R.id.item_routing_mode);
        itemFallbackHosts = findViewById(R.id.item_fallback_hosts);

        valueServerAddr = findViewById(R.id.value_server_addr);
        valueServerIp = findViewById(R.id.value_server_ip);
        valueDnsServer = findViewById(R.id.value_dns_server);
        valueEchDomain = findViewById(R.id.value_ech_domain);
        valueToken = findViewById(R.id.value_token);
        valueRoutingMode = findViewById(R.id.value_routing_mode);
        valueFallbackHosts = findViewById(R.id.value_fallback_hosts);
        
        Button btnBack = findViewById(R.id.btn_back);
        Button btnSave = findViewById(R.id.btn_save);
        
        btnBack.setOnClickListener(v -> finish());
        findViewById(R.id.btn_profile_add).setOnClickListener(v -> showAddProfileDialog());
        findViewById(R.id.btn_profile_rename).setOnClickListener(v -> showRenameProfileDialog());
        findViewById(R.id.btn_profile_delete).setOnClickListener(v -> showDeleteProfileDialog());
        btnSave.setOnClickListener(v -> {
            saveConfig();
            Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadProfiles() {
        Set<String> profileSet = prefs.getStringSet("profile_list", new HashSet<>());
        profiles = new ArrayList<>(profileSet);
        Collections.sort(profiles);
        
        if (profiles.isEmpty()) {
            profiles.add(getString(R.string.default_profile_name));
            Set<String> newSet = new HashSet<>(profiles);
            prefs.edit().putStringSet("profile_list", newSet).apply();
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, profiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(adapter);
        
        String lastProfile = prefs.getString("last_profile", profiles.get(0));
        int position = profiles.indexOf(lastProfile);
        if (position >= 0) {
            profileSpinner.setSelection(position);
            currentProfile = lastProfile;
            loadProfileConfig();
        }
    }

    private void setupListeners() {
        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentProfile = profiles.get(position);
                prefs.edit().putString("last_profile", currentProfile).apply();
                loadProfileConfig();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        itemServerAddr.setOnClickListener(v -> showEditDialog(
            getString(R.string.server_addr),
            valueServerAddr.getText().toString(),
            "server_addr",
            getString(R.string.server_addr_hint)
        ));
        
        itemServerIp.setOnClickListener(v -> showEditDialog(
            getString(R.string.server_ip),
            valueServerIp.getText().toString(),
            "server_ip",
            getString(R.string.server_ip_hint)
        ));
        
        itemDnsServer.setOnClickListener(v -> showEditDialog(
            getString(R.string.dns_server),
            valueDnsServer.getText().toString(),
            "dns_server",
            getString(R.string.dns_server_hint)
        ));
        
        itemEchDomain.setOnClickListener(v -> showEditDialog(
            getString(R.string.ech_domain),
            valueEchDomain.getText().toString(),
            "ech_domain",
            getString(R.string.ech_domain_hint)
        ));
        
        itemToken.setOnClickListener(v -> showEditDialog(
            getString(R.string.token),
            valueToken.getText().toString(),
            "token",
            getString(R.string.optional)
        ));
        
        itemFallbackHosts.setOnClickListener(v -> showEditDialog(
            getString(R.string.fallback_hosts),
            valueFallbackHosts.getText().toString(),
            "fallback_hosts",
            getString(R.string.fallback_hosts_hint)
        ));
        
        itemRoutingMode.setOnClickListener(v -> showRoutingModeDialog());
    }

    private void loadProfileConfig() {
        if (currentProfile == null) return;
        
        String prefix = "profile_" + currentProfile + "_";
        
        String serverAddr = prefs.getString(prefix + "server_addr", "");
        String serverIp = prefs.getString(prefix + "server_ip", "mfa.gov.ua");
        String dnsServer = prefs.getString(prefix + "dns_server", "dns.alidns.com/dns-query");
        String echDomain = prefs.getString(prefix + "ech_domain", "cloudflare-ech.com");
        String token = prefs.getString(prefix + "token", "");
        String routingMode = prefs.getString(prefix + "routing_mode", "bypass_cn");
        String fallbackHosts = prefs.getString(prefix + "fallback_hosts", "");
        
        valueServerAddr.setText(serverAddr.isEmpty() ? getString(R.string.not_configured) : serverAddr);
        valueServerIp.setText(serverIp);
        valueDnsServer.setText(dnsServer);
        valueEchDomain.setText(echDomain);
        valueToken.setText(token.isEmpty() ? getString(R.string.optional) : "••••••");
        valueFallbackHosts.setText(fallbackHosts.isEmpty() ? getString(R.string.optional) : fallbackHosts);
        
        String routingText = getString(R.string.bypass_cn);
        if ("global".equals(routingMode)) {
            routingText = getString(R.string.global_proxy);
        } else if ("none".equals(routingMode)) {
            routingText = getString(R.string.direct);
        }
        valueRoutingMode.setText(routingText);
    }

    private void showEditDialog(String title, String currentValue, String key, String hint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        
        final EditText input = new EditText(this);
        input.setHint(hint);
        
        // 如果是显示的占位符，清空输入框
        if (currentValue.equals(getString(R.string.not_configured)) || 
            currentValue.equals(getString(R.string.optional)) ||
            currentValue.startsWith("••")) {
            input.setText("");
        } else {
            input.setText(currentValue);
        }
        
        builder.setView(input);
        
        builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
            String value = input.getText().toString().trim();
            String prefix = "profile_" + currentProfile + "_";
            prefs.edit().putString(prefix + key, value).apply();
            loadProfileConfig();
        });
        
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void showRoutingModeDialog() {
        String[] modes = {
            getString(R.string.bypass_cn),
            getString(R.string.global_proxy),
            getString(R.string.direct)
        };
        
        String[] modeValues = {"bypass_cn", "global", "none"};
        
        String prefix = "profile_" + currentProfile + "_";
        String currentMode = prefs.getString(prefix + "routing_mode", "bypass_cn");
        
        int selectedIndex = 0;
        for (int i = 0; i < modeValues.length; i++) {
            if (modeValues[i].equals(currentMode)) {
                selectedIndex = i;
                break;
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.routing_mode));
        builder.setSingleChoiceItems(modes, selectedIndex, (dialog, which) -> {
            prefs.edit().putString(prefix + "routing_mode", modeValues[which]).apply();
            loadProfileConfig();
            dialog.dismiss();
        });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void saveConfig() {
        // 配置已经在各个对话框中保存了
        prefs.edit().putString("last_profile", currentProfile).apply();
    }

    // ===== 节点管理：添加 / 改名 / 删除 =====
    private void saveProfileList() {
        prefs.edit().putStringSet("profile_list", new HashSet<>(profiles)).apply();
    }

    private void refreshSpinner(String select) {
        Collections.sort(profiles);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, profiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(adapter);
        int pos = profiles.indexOf(select);
        if (pos < 0) pos = 0;
        currentProfile = profiles.get(pos);
        profileSpinner.setSelection(pos);
        prefs.edit().putString("last_profile", currentProfile).apply();
        loadProfileConfig();
    }

    private String validateName(String name, String except) {
        if (name.isEmpty()) return "名称不能为空";
        if (name.contains("_")) return "名称不能包含下划线 _";
        if (!name.equals(except) && profiles.contains(name)) return "已有同名节点";
        return null;
    }

    private void showAddProfileDialog() {
        final EditText input = new EditText(this);
        input.setHint("例如：日本节点");
        new AlertDialog.Builder(this)
            .setTitle("添加节点")
            .setView(input)
            .setPositiveButton(getString(R.string.ok), (d, w) -> {
                String name = input.getText().toString().trim();
                String err = validateName(name, null);
                if (err != null) { Toast.makeText(this, err, Toast.LENGTH_SHORT).show(); return; }
                // 新节点复制当前节点的设置，方便只改几项
                copyProfileKeys(currentProfile, name, false);
                profiles.add(name);
                saveProfileList();
                refreshSpinner(name);
                Toast.makeText(this, "已添加，已复制当前节点设置，请修改后保存", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }

    private void showRenameProfileDialog() {
        if (currentProfile == null) return;
        final String old = currentProfile;
        final EditText input = new EditText(this);
        input.setText(old);
        new AlertDialog.Builder(this)
            .setTitle("节点改名")
            .setView(input)
            .setPositiveButton(getString(R.string.ok), (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.equals(old)) return;
                String err = validateName(name, old);
                if (err != null) { Toast.makeText(this, err, Toast.LENGTH_SHORT).show(); return; }
                copyProfileKeys(old, name, true);
                profiles.remove(old);
                profiles.add(name);
                saveProfileList();
                refreshSpinner(name);
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }

    private void showDeleteProfileDialog() {
        if (currentProfile == null) return;
        if (profiles.size() <= 1) {
            Toast.makeText(this, "至少要保留一个节点", Toast.LENGTH_SHORT).show();
            return;
        }
        final String target = currentProfile;
        new AlertDialog.Builder(this)
            .setTitle("删除节点")
            .setMessage("确定删除「" + target + "」？")
            .setPositiveButton(getString(R.string.ok), (d, w) -> {
                SharedPreferences.Editor ed = prefs.edit();
                String prefix = "profile_" + target + "_";
                for (String k : prefs.getAll().keySet()) {
                    if (k.startsWith(prefix)) ed.remove(k);
                }
                ed.apply();
                profiles.remove(target);
                saveProfileList();
                refreshSpinner(profiles.get(0));
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }

    private void copyProfileKeys(String from, String to, boolean removeOld) {
        if (from == null) return;
        String fp = "profile_" + from + "_";
        String tp = "profile_" + to + "_";
        SharedPreferences.Editor ed = prefs.edit();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            String k = e.getKey();
            if (!k.startsWith(fp)) continue;
            String suffix = k.substring(fp.length());
            if (suffix.contains("_") && !isKnownKey(suffix)) continue; // 防止前缀相同的其他节点
            if (e.getValue() instanceof String) ed.putString(tp + suffix, (String) e.getValue());
            if (removeOld) ed.remove(k);
        }
        ed.apply();
    }

    private boolean isKnownKey(String s) {
        return s.equals("server_addr") || s.equals("server_ip") || s.equals("dns_server")
            || s.equals("ech_domain") || s.equals("token") || s.equals("routing_mode")
            || s.equals("fallback_hosts");
    }
}
