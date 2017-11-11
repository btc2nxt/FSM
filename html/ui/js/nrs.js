/**
 * @depends {3rdparty/jquery-2.1.0.js}
 * @depends {3rdparty/bootstrap.js}
 * @depends {3rdparty/big.js}
 * @depends {3rdparty/jsbn.js}
 * @depends {3rdparty/jsbn2.js}
 * @depends {3rdparty/pako.js}
 * @depends {3rdparty/webdb.js}
 * @depends {3rdparty/ajaxmultiqueue.js}
 * @depends {3rdparty/growl.js}
 * @depends {3rdparty/zeroclipboard.js}
 * @depends {crypto/curve25519.js}
 * @depends {crypto/curve25519_.js}
 * @depends {crypto/passphrasegenerator.js}
 * @depends {crypto/sha256worker.js}
 * @depends {crypto/3rdparty/cryptojs/aes.js}
 * @depends {crypto/3rdparty/cryptojs/sha256.js}
 * @depends {crypto/3rdparty/jssha256.js}
 * @depends {crypto/3rdparty/seedrandom.js}
 * @depends {util/converters.js}
 * @depends {util/extensions.js}
 * @depends {util/nxtaddress.js}
 */
var NRS = (function(NRS, $, undefined) {
	"use strict";

	NRS.server = "";
	NRS.state = {};
	NRS.blocks = [];
	NRS.genesis = "1763558929574856152";
	NRS.genesisRS = "NFD-G3GS-J4SW-T2WM-3DDYJ";

	NRS.account = "";
	NRS.accountRS = ""
	NRS.publicKey = "";
	NRS.accountInfo = {};

	NRS.database = null;
	NRS.databaseSupport = false;

	NRS.settings = {};
	NRS.contacts = {};

	NRS.isTestNet = false;
	NRS.isLocalHost = false;
	NRS.isForging = false;
	NRS.isLeased = false;

	NRS.lastBlockHeight = 0;
	NRS.downloadingBlockchain = false;

	NRS.rememberPassword = false;
	NRS.selectedContext = null;

	NRS.currentPage = "dashboard";
	NRS.currentSubPage = "";
	NRS.pageNumber = 1;
	//NRS.itemsPerPage = 50;  /* Now set in nrs.settings.js */

	NRS.pages = {};
	NRS.incoming = {};
	
	if(!_checkDOMenabled())
		NRS.hasLocalStorage = false;
	else
		NRS.hasLocalStorage = true;
	
	NRS.inApp = false;
	NRS.appVersion = "";
	NRS.appPlatform = "";
	NRS.assetTableKeys = [];

	var stateInterval;
	var stateIntervalSeconds = 30;
	var isScanning = false;

	NRS.init = function() {
		if (window.location.port != "9876") {
			$(".testnet_only").hide();
		} else {
			NRS.isTestNet = true;			
			$(".testnet_only, #testnet_login, #testnet_warning").show();
		}

		if (!NRS.server) {
			var hostName = window.location.hostname.toLowerCase();
			NRS.isLocalHost = hostName == "localhost" || hostName == "127.0.0.1" || NRS.isPrivateIP(hostName);
		}

		if (!NRS.isLocalHost) {
			$(".remote_warning").show();
		}

		try {
			window.localStorage;
		} catch (err) {
			NRS.hasLocalStorage = false;
		}

		if (NRS.getCookie("remember_passphrase")) {
			$("#remember_password").prop("checked", true);
		}

		NRS.createDatabase(function() {
			NRS.getSettings();
		});

		NRS.getState(function() {
			setTimeout(function() {
				NRS.checkAliasVersions();
			}, 5000);
		});

		NRS.showLockscreen();

		if (window.parent) {
			var match = window.location.href.match(/\?app=?(win|mac|lin)?\-?([\d\.]+)?/i);

			if (match) {
				NRS.inApp = true;
				if (match[1]) {
					NRS.appPlatform = match[1];
				}
				if (match[2]) {
					NRS.appVersion = match[2];
				}

				if (!NRS.appPlatform || NRS.appPlatform == "mac") {
					var macVersion = navigator.userAgent.match(/OS X 10_([0-9]+)/i);
					if (macVersion && macVersion[1]) {
						macVersion = parseInt(macVersion[1]);

						if (macVersion < 9) {
							$(".modal").removeClass("fade");
						}
					}
				}

				$("#show_console").hide();

				parent.postMessage("loaded", "*");

				window.addEventListener("message", receiveMessage, false);
			}
		}

		NRS.setStateInterval(30);

		if (!NRS.isTestNet) {
			setInterval(NRS.checkAliasVersions, 1000 * 60 * 60);
		}

		NRS.allowLoginViaEnter();

		NRS.automaticallyCheckRecipient();

		$(".show_popover").popover({
			"trigger": "hover"
		});

		$("#dashboard_transactions_table, #transactions_table").on("mouseenter", "td.confirmations", function() {
			$(this).popover("show");
		}).on("mouseleave", "td.confirmations", function() {
			$(this).popover("destroy");
			$(".popover").remove();
		});

		_fix();

		$(window).on("resize", function() {
			_fix();

			if (NRS.currentPage == "asset_exchange") {
				NRS.positionAssetSidebar();
			}
		});

		$("[data-toggle='tooltip']").tooltip();

		$(".sidebar .treeview").tree();

		$("#dgs_search_account_top, #dgs_search_account_center").mask("NFD-****-****-****-*****", {
			"unmask": false
		});

		/*
		$("#asset_exchange_search input[name=q]").addClear({
			right: 0,
			top: 4,
			onClear: function(input) {
				$("#asset_exchange_search").trigger("submit");
			}
		});

		$("#id_search input[name=q], #alias_search input[name=q]").addClear({
			right: 0,
			top: 4
		});*/
	}

	function _fix() {
		var height = $(window).height() - $("body > .header").height();
		//$(".wrapper").css("min-height", height + "px");
		var content = $(".wrapper").height();

		$(".content.content-stretch:visible").width($(".page:visible").width());

		if (content > height) {
			$(".left-side, html, body").css("min-height", content + "px");
		} else {
			$(".left-side, html, body").css("min-height", height + "px");
		}
	}

	NRS.setStateInterval = function(seconds) {
		if (seconds == stateIntervalSeconds && stateInterval) {
			return;
		}

		if (stateInterval) {
			clearInterval(stateInterval);
		}

		stateIntervalSeconds = seconds;

		stateInterval = setInterval(function() {
			NRS.getState();
		}, 1000 * seconds);
	}

	NRS.getState = function(callback) {
		NRS.sendRequest("getBlockchainStatus", function(response) {
			if (response.errorCode) {
				//todo
			} else {
				var firstTime = !("lastBlock" in NRS.state);
				var previousLastBlock = (firstTime ? "0" : NRS.state.lastBlock);

				NRS.state = response;

				if (firstTime) {
					$("#nrs_version").html(NRS.state.version).removeClass("loading_dots");
					NRS.getBlock(NRS.state.lastBlock, NRS.handleInitialBlocks);
				} else if (NRS.state.isScanning) {
					//do nothing but reset NRS.state so that when isScanning is done, everything is reset.
					isScanning = true;
				} else if (isScanning) {
					//rescan is done, now we must reset everything...
					isScanning = false;
					NRS.blocks = [];
					NRS.tempBlocks = [];
					NRS.getBlock(NRS.state.lastBlock, NRS.handleInitialBlocks);
					if (NRS.account) {
						NRS.getInitialTransactions();
						NRS.getAccountInfo();
					}
				} else if (previousLastBlock != NRS.state.lastBlock) {
					NRS.tempBlocks = [];
					if (NRS.account) {
						NRS.getAccountInfo();
					}
					NRS.getBlock(NRS.state.lastBlock, NRS.handleNewBlocks);
					if (NRS.account) {
						NRS.getNewTransactions();
					}
				} else {
					if (NRS.account) {
						NRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
							NRS.handleIncomingTransactions(unconfirmedTransactions, false);
						});
					}
					//only done so that download progress meter updates correctly based on lastFeederHeight
					if (NRS.downloadingBlockchain) {
						NRS.updateBlockchainDownloadProgress();
					}
				}

				if (callback) {
					callback();
				}
			}
			/* Checks if the client is connected to active peers */
			NRS.checkConnected();
		});
	}

	$("#logo, .sidebar-menu a").click(function(e, data) {
		if ($(this).hasClass("ignore")) {
			$(this).removeClass("ignore");
			return;
		}

		e.preventDefault();

		if ($(this).data("toggle") == "modal") {
			return;
		}

		var page = $(this).data("page");

		if (page == NRS.currentPage) {
			if (data && data.callback) {
				data.callback();
			}
			return;
		}

		$(".page").hide();

		$(document.documentElement).scrollTop(0);

		$("#" + page + "_page").show();

		$(".content-header h1").find(".loading_dots").remove();

		var changeActive = !($(this).closest("ul").hasClass("treeview-menu"));

		if (changeActive) {
			var currentActive = $("ul.sidebar-menu > li.active");

			if (currentActive.hasClass("treeview")) {
				currentActive.children("a").first().addClass("ignore").click();
			} else {
				currentActive.removeClass("active");
			}

			if ($(this).attr("id") && $(this).attr("id") == "logo") {
				$("#dashboard_link").addClass("active");
			} else {
				$(this).parent().addClass("active");
			}
		}

		if (NRS.currentPage != "messages") {
			$("#inline_message_password").val("");
		}

		//NRS.previousPage = NRS.currentPage;
		NRS.currentPage = page;
		NRS.currentSubPage = "";
		NRS.pageNumber = 1;
		NRS.showPageNumbers = false;

		if (NRS.pages[page]) {
			NRS.pageLoading();

			if (data && data.callback) {
				NRS.pages[page](data.callback);
			} else if (data) {
				NRS.pages[page](data);
			} else {
				NRS.pages[page]();
			}
		}
	});

	$("button.goto-page, a.goto-page").click(function(event) {
		event.preventDefault();

		NRS.goToPage($(this).data("page"));
	});

	NRS.loadPage = function(page, callback) {
		NRS.pageLoading();
		NRS.pages[page](callback);
	}

	NRS.goToPage = function(page, callback) {
		var $link = $("ul.sidebar-menu a[data-page=" + page + "]");

		if ($link.length > 1) {
			if ($link.last().is(":visible")) {
				$link = $link.last();
			} else {
				$link = $link.first();
			}
		}

		if ($link.length == 1) {
			if (callback) {
				$link.trigger("click", [{
					"callback": callback
				}]);
			} else {
				$link.trigger("click");
			}
		} else {
			NRS.currentPage = page;
			NRS.currentSubPage = "";
			NRS.pageNumber = 1;
			NRS.showPageNumbers = false;

			$("ul.sidebar-menu a.active").removeClass("active");
			$(".page").hide();
			$("#" + page + "_page").show();
			if (NRS.pages[page]) {
				NRS.pageLoading();
				NRS.pages[page](callback);
			}
		}
	}

	NRS.pageLoading = function() {
		NRS.hasMorePages = false;

		var $pageHeader = $("#" + NRS.currentPage + "_page .content-header h1");
		$pageHeader.find(".loading_dots").remove();
		$pageHeader.append("<span class='loading_dots'><span>.</span><span>.</span><span>.</span></span>");
	}

	NRS.pageLoaded = function(callback) {
		var $currentPage = $("#" + NRS.currentPage + "_page");

		$currentPage.find(".content-header h1 .loading_dots").remove();

		if ($currentPage.hasClass("paginated")) {
			NRS.addPagination();
		}

		if (callback) {
			callback();
		}
	}

	NRS.addPagination = function(section) {
		var output = "";

		if (NRS.pageNumber == 2) {
			output += "<a href='#' data-page='1'>&laquo; " + $.t("previous_page") + "</a>";
		} else if (NRS.pageNumber > 2) {
			//output += "<a href='#' data-page='1'>&laquo; First Page</a>";
			output += " <a href='#' data-page='" + (NRS.pageNumber - 1) + "'>&laquo; " + $.t("previous_page") + "</a>";
		}
		if (NRS.hasMorePages) {
			if (NRS.pageNumber > 1) {
				output += "&nbsp;&nbsp;&nbsp;";
			}
			output += " <a href='#' data-page='" + (NRS.pageNumber + 1) + "'>" + $.t("next_page") + " &raquo;</a>";
		}

		var $paginationContainer = $("#" + NRS.currentPage + "_page .data-pagination");

		if ($paginationContainer.length) {
			$paginationContainer.html(output);
		}
	}

	$(".data-pagination").on("click", "a", function(e) {
		e.preventDefault();

		NRS.goToPageNumber($(this).data("page"));
	});

	NRS.goToPageNumber = function(pageNumber) {
		/*if (!pageLoaded) {
			return;
		}*/
		NRS.pageNumber = pageNumber;

		NRS.pageLoading();

		NRS.pages[NRS.currentPage]();
	}

	NRS.createDatabase = function(callback) {
		var schema = {
			contacts: {
				id: {
					"primary": true,
					"autoincrement": true,
					"type": "NUMBER"
				},
				name: "VARCHAR(100) COLLATE NOCASE",
				email: "VARCHAR(200)",
				account: "VARCHAR(25)",
				accountRS: "VARCHAR(25)",
				description: "TEXT"
			},
			assets: {
				account: "VARCHAR(25)",
				accountRS: "VARCHAR(25)",
				asset: {
					"primary": true,
					"type": "VARCHAR(25)"
				},
				description: "TEXT",
				name: "VARCHAR(10)",
				decimals: "NUMBER",
				quantityQNT: "VARCHAR(15)",
				groupName: "VARCHAR(30) COLLATE NOCASE"
			},
			data: {
				id: {
					"primary": true,
					"type": "VARCHAR(40)"
				},
				contents: "TEXT"
			}
		};

		NRS.assetTableKeys = ["account", "accountRS", "asset", "description", "name", "position", "decimals", "quantityQNT", "groupName"];

		try {
			NRS.database = new WebDB("NRS_USER_DB", schema, 2, 4, function(error, db) {
				if (!error) {
					NRS.databaseSupport = true;

					NRS.loadContacts();

					NRS.database.select("data", [{
						"id": "asset_exchange_version"
					}], function(error, result) {
						if (!result || !result.length) {
							NRS.database.delete("assets", [], function(error, affected) {
								if (!error) {
									NRS.database.insert("data", {
										"id": "asset_exchange_version",
										"contents": 2
									});
								}
							});
						}
					});

					NRS.database.select("data", [{
						"id": "closed_groups"
					}], function(error, result) {
						if (result && result.length) {
							NRS.closedGroups = result[0].contents.split("#");
						} else {
							NRS.database.insert("data", {
								id: "closed_groups",
								contents: ""
							});
						}
					});
					if (callback) {
						callback();
					}
				} else {
					if (callback) {
						callback();
					}
				}
			});
		} catch (err) {
			NRS.database = null;
			NRS.databaseSupport = false;
			if (callback) {
				callback();
			}
		}
	}
	
	/* Display connected state in Sidebar */
	NRS.checkConnected = function() {
		NRS.sendRequest("getPeers+", {
			"state": "CONNECTED"
		}, function(response) {
			if (response.peers && response.peers.length) {
				$("#connected_indicator").addClass("connected");
				$("#connected_indicator span").html($.t("Connected")).attr("data-i18n", "connected");
				$("#connected_indicator").show();
			} else {
				$("#connected_indicator").removeClass("connected");
				$("#connected_indicator span").html($.t("Not Connected")).attr("data-i18n", "not_connected");
				$("#connected_indicator").show();
			}
		});
	}

	NRS.getAccountInfo = function(firstRun, callback) {
		NRS.sendRequest("getAccount", {
			"account": NRS.account
		}, function(response) {
			var previousAccountInfo = NRS.accountInfo;

			NRS.accountInfo = response;

			if (response.errorCode) {
				$("#account_balance, #account_balance_sidebar, #account_nr_assets, #account_assets_balance, #account_message_count").html("0");
				
				if (NRS.accountInfo.errorCode == 5) {
					if (NRS.downloadingBlockchain) {
						if (NRS.newlyCreatedAccount) {
							$("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html($.t("status_new_account", {
								"account_id": String(NRS.accountRS).escapeHTML(),
								"public_key": String(NRS.publicKey).escapeHTML()
							}) + "<br /><br />" + $.t("status_blockchain_downloading")).show();
						} else {
							$("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html($.t("status_blockchain_downloading")).show();
						}
					} else if (NRS.state && NRS.state.isScanning) {
						$("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html($.t("status_blockchain_rescanning")).show();
					} else {
						$("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html($.t("status_new_account", {
							"account_id": String(NRS.accountRS).escapeHTML(),
							"public_key": String(NRS.publicKey).escapeHTML()
						})).show();
					}
				} else {
					$("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html(NRS.accountInfo.errorDescription ? NRS.accountInfo.errorDescription.escapeHTML() : $.t("error_unknown")).show();
				}
			} else {
				if (NRS.accountRS && NRS.accountInfo.accountRS != NRS.accountRS) {
					$.growl("Generated Reed Solomon address different from the one in the blockchain!", {
						"type": "danger"
					});
					NRS.accountRS = NRS.accountInfo.accountRS;
				}

				if (NRS.downloadingBlockchain) {
					$("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html($.t("status_blockchain_downloading")).show();
				} else if (NRS.state && NRS.state.isScanning) {
					$("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html($.t("status_blockchain_rescanning")).show();
				} else if (!NRS.accountInfo.publicKey) {
					$("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html($.t("no_public_key_warning") + " " + $.t("public_key_actions")).show();
				} else {
					$("#dashboard_message").hide();
				}

				//only show if happened within last week
				var showAssetDifference = (!NRS.downloadingBlockchain || (NRS.blocks && NRS.blocks[0] && NRS.state && NRS.state.time - NRS.blocks[0].timestamp < 60 * 60 * 24 * 7));

				if (NRS.databaseSupport) {
					NRS.database.select("data", [{
						"id": "asset_balances_" + NRS.account
					}], function(error, asset_balance) {
						if (asset_balance && asset_balance.length) {
							var previous_balances = asset_balance[0].contents;

							if (!NRS.accountInfo.assetBalances) {
								NRS.accountInfo.assetBalances = [];
							}

							var current_balances = JSON.stringify(NRS.accountInfo.assetBalances);

							if (previous_balances != current_balances) {
								if (previous_balances != "undefined" && typeof previous_balances != "undefined") {
									previous_balances = JSON.parse(previous_balances);
								} else {
									previous_balances = [];
								}
								NRS.database.update("data", {
									contents: current_balances
								}, [{
									id: "asset_balances_" + NRS.account
								}]);
								if (showAssetDifference) {
									NRS.checkAssetDifferences(NRS.accountInfo.assetBalances, previous_balances);
								}
							}
						} else {
							NRS.database.insert("data", {
								id: "asset_balances_" + NRS.account,
								contents: JSON.stringify(NRS.accountInfo.assetBalances)
							});
						}
					});
				} else if (showAssetDifference && previousAccountInfo && previousAccountInfo.assetBalances) {
					var previousBalances = JSON.stringify(previousAccountInfo.assetBalances);
					var currentBalances = JSON.stringify(NRS.accountInfo.assetBalances);

					if (previousBalances != currentBalances) {
						NRS.checkAssetDifferences(NRS.accountInfo.assetBalances, previousAccountInfo.assetBalances);
					}
				}

				$("#account_balance, #account_balance_sidebar").html(NRS.formatStyledAmount(response.unconfirmedBalanceNQT));
				$("#account_forged_balance").html(NRS.formatStyledAmount(response.forgedBalanceNQT));

				/*** Need to clean up and optimize code if possible ***/
				var nr_assets = 0;
				var assets = {
					"asset": [],
					"quantity": {},
					"trades": {}
				};
				var tradeNum = 0;
				var assets_LastTrade = [];
				if (response.assetBalances) {
					for (var i = 0; i < response.assetBalances.length; i++) {
						if (response.assetBalances[i].balanceQNT != "0") {
							nr_assets++;
							assets.quantity[response.assetBalances[i].asset] = response.assetBalances[i].balanceQNT;
							assets.asset.push(response.assetBalances[i].asset);
							NRS.sendRequest("getTrades", {
								"asset": response.assetBalances[i].asset,
								"firstIndex": 0,
								"lastIndex": 0
							}, function(responseTrade, input) {
								if (responseTrade.trades && responseTrade.trades.length) {
									assets.trades[input.asset] = responseTrade.trades[0].priceNQT/100000000;
								}
								else{
									assets.trades[input.asset] = 0;
								}
								if (tradeNum == nr_assets-1)
									NRS.updateAssetsValue(assets);
								else
									tradeNum++;
							});
							
						}
					}
				}
				else {
					$("#account_assets_balance").html(0);
				}
								
				$("#account_nr_assets").html(nr_assets);

				/* Display message count in top and limit to 100 for now because of possible performance issues*/	
				NRS.sendRequest("getAccountTransactions+", {
					"account": NRS.account,
					"type": 1,
					"subtype": 0,
					"firstIndex": 0,
					"lastIndex": 99
				}, function(response) {
					if (response.transactions && response.transactions.length) {
						if (response.transactions.length > 99)
							$("#account_message_count").empty().append("99+");
						else
							$("#account_message_count").empty().append(response.transactions.length);
					} else {
						$("#account_message_count").empty().append("0");
					}
				});	
				
				/***  ******************   ***/
				
			

				if (NRS.lastBlockHeight) {
					var isLeased = NRS.lastBlockHeight >= NRS.accountInfo.currentLeasingHeightFrom;
					if (isLeased != NRS.IsLeased) {
						var leasingChange = true;
						NRS.isLeased = isLeased;
					}
				} else {
					var leasingChange = false;
				}

				if (leasingChange ||
					(response.currentLeasingHeightFrom != previousAccountInfo.currentLeasingHeightFrom) ||
					(response.lessors && !previousAccountInfo.lessors) ||
					(!response.lessors && previousAccountInfo.lessors) ||
					(response.lessors && previousAccountInfo.lessors && response.lessors.sort().toString() != previousAccountInfo.lessors.sort().toString())) {
					NRS.updateAccountLeasingStatus();
				}

				if (response.name) {
					$("#account_name").html(response.name.escapeHTML()).removeAttr("data-i18n");
				}
			}

			if (firstRun) {
				$("#account_balance, #account_balance_sidebar, #account_assets_balance, #account_nr_assets, #account_message_count").removeClass("loading_dots");
			}

			if (callback) {
				callback();
			}
		});
	}

	NRS.updateAssetsValue = function(assets) {
		var assetTotal = 0;
		for (var i = 0; i < assets.asset.length; i++) {
			if (assets.quantity[assets.asset[i]] && assets.trades[assets.asset[i]])
				assetTotal += assets.quantity[assets.asset[i]]*assets.trades[assets.asset[i]];
		}
		
		$("#account_assets_balance").html(NRS.formatStyledAmount(assetTotal));
	}

	NRS.updateAccountLeasingStatus = function() {
		var accountLeasingLabel = "";
		var accountLeasingStatus = "";

		if (NRS.lastBlockHeight >= NRS.accountInfo.currentLeasingHeightFrom) {
			accountLeasingLabel = $.t("leased_out");
			accountLeasingStatus = $.t("balance_is_leased_out", {
				"start": String(NRS.accountInfo.currentLeasingHeightFrom).escapeHTML(),
				"end": String(NRS.accountInfo.currentLeasingHeightTo).escapeHTML(),
				"account": String(NRS.accountInfo.currentLessee).escapeHTML()
			});
			$("#lease_balance_message").html($.t("balance_leased_out_help"));
		} else if (NRS.lastBlockHeight < NRS.accountInfo.currentLeasingHeightTo) {
			accountLeasingLabel = $.t("leased_soon");
			accountLeasingStatus = $.t("balance_will_be_leased_out", {
				"start": String(NRS.accountInfo.currentLeasingHeightFrom).escapeHTML(),
				"end": String(NRS.accountInfo.currentLeasingHeightTo).escapeHTML(),
				"account": String(NRS.accountInfo.currentLessee).escapeHTML()
			});
			$("#lease_balance_message").html($.t("balance_leased_out_help"));
		} else {
			accountLeasingStatus = $.t("balance_not_leased_out");
			$("#lease_balance_message").html($.t("balance_leasing_help"));
		}

		if (NRS.accountInfo.effectiveBalanceNXT == 0) {
			$("#forging_indicator").removeClass("forging");
			$("#forging_indicator span").html($.t("not_forging")).attr("data-i18n", "not_forging");
			$("#forging_indicator").show();
			NRS.isForging = false;
		}

		//no reed solomon available? do it myself? todo
		if (NRS.accountInfo.lessors) {
			if (accountLeasingLabel) {
				accountLeasingLabel += ", ";
				accountLeasingStatus += "<br /><br />";
			}

			accountLeasingLabel += $.t("x_lessor", {
				"count": NRS.accountInfo.lessors.length
			});
			accountLeasingStatus += $.t("x_lessor_lease", {
				"count": NRS.accountInfo.lessors.length
			});

			var rows = "";

			for (var i = 0; i < NRS.accountInfo.lessors.length; i++) {
				var lessor = NRS.convertNumericToRSAccountFormat(NRS.accountInfo.lessors[i]);

				rows += "<tr><td><a href='#' data-user='" + String(lessor).escapeHTML() + "'>" + NRS.getAccountTitle(lessor) + "</a></td></tr>";
			}

			$("#account_lessor_table tbody").empty().append(rows);
			$("#account_lessor_container").show();
		} else {
			$("#account_lessor_table tbody").empty();
			$("#account_lessor_container").hide();
		}

		if (accountLeasingLabel) {
			$("#account_leasing").html(accountLeasingLabel).show();
		} else {
			$("#account_leasing").hide();
		}

		if (accountLeasingStatus) {
			$("#account_leasing_status").html(accountLeasingStatus).show();
		} else {
			$("#account_leasing_status").hide();
		}
	}

	NRS.checkAssetDifferences = function(current_balances, previous_balances) {
		var current_balances_ = {};
		var previous_balances_ = {};

		if (previous_balances.length) {
			for (var k in previous_balances) {
				previous_balances_[previous_balances[k].asset] = previous_balances[k].balanceQNT;
			}
		}

		if (current_balances.length) {
			for (var k in current_balances) {
				current_balances_[current_balances[k].asset] = current_balances[k].balanceQNT;
			}
		}

		var diff = {};

		for (var k in previous_balances_) {
			if (!(k in current_balances_)) {
				diff[k] = "-" + previous_balances_[k];
			} else if (previous_balances_[k] !== current_balances_[k]) {
				var change = (new BigInteger(current_balances_[k]).subtract(new BigInteger(previous_balances_[k]))).toString();
				diff[k] = change;
			}
		}

		for (k in current_balances_) {
			if (!(k in previous_balances_)) {
				diff[k] = current_balances_[k]; // property is new
			}
		}

		var nr = Object.keys(diff).length;

		if (nr == 0) {
			return;
		} else if (nr <= 3) {
			for (k in diff) {
				NRS.sendRequest("getAsset", {
					"asset": k,
					"_extra": {
						"asset": k,
						"difference": diff[k]
					}
				}, function(asset, input) {
					if (asset.errorCode) {
						return;
					}
					asset.difference = input["_extra"].difference;
					asset.asset = input["_extra"].asset;

					if (asset.difference.charAt(0) != "-") {
						var quantity = NRS.formatQuantity(asset.difference, asset.decimals)

						if (quantity != "0") {
							$.growl($.t("you_received_assets", {
								"asset": String(asset.asset).escapeHTML(),
								"name": String(asset.name).escapeHTML(),
								"count": quantity
							}), {
								"type": "success"
							});
						}
					} else {
						asset.difference = asset.difference.substring(1);

						var quantity = NRS.formatQuantity(asset.difference, asset.decimals)

						if (quantity != "0") {
							$.growl($.t("you_sold_assets", {
								"asset": String(asset.asset).escapeHTML(),
								"name": String(asset.name).escapeHTML(),
								"count": quantity
							}), {
								"type": "success"
							});
						}
					}
				});
			}
		} else {
			$.growl($.t("multiple_assets_differences"), {
				"type": "success"
			});
		}
	}

	NRS.checkLocationHash = function(password) {
		if (window.location.hash) {
			var hash = window.location.hash.replace("#", "").split(":")

			if (hash.length == 2) {
				if (hash[0] == "message") {
					var $modal = $("#send_message_modal");
				} else if (hash[0] == "send") {
					var $modal = $("#send_money_modal");
				} else if (hash[0] == "asset") {
					NRS.goToAsset(hash[1]);
					return;
				} else {
					var $modal = "";
				}

				if ($modal) {
					var account_id = String($.trim(hash[1]));
					if (!/^\d+$/.test(account_id) && account_id.indexOf("@") !== 0) {
						account_id = "@" + account_id;
					}

					$modal.find("input[name=recipient]").val(account_id.unescapeHTML()).trigger("blur");
					if (password && typeof password == "string") {
						$modal.find("input[name=secretPhrase]").val(password);
					}
					$modal.modal("show");
				}
			}

			window.location.hash = "#";
		}
	}

	NRS.updateBlockchainDownloadProgress = function() {
		if (NRS.state.lastBlockchainFeederHeight && NRS.state.numberOfBlocks < NRS.state.lastBlockchainFeederHeight) {
			var percentage = parseInt(Math.round((NRS.state.numberOfBlocks / NRS.state.lastBlockchainFeederHeight) * 100), 10);
		} else {
			var percentage = 100;
		}

		if (percentage == 100) {
			$("#downloading_blockchain .progress").hide();
		} else {
			$("#downloading_blockchain .progress").show();
			$("#downloading_blockchain .progress-bar").css("width", percentage + "%");
			$("#downloading_blockchain .sr-only").html($.t("percent_complete", {
				"percent": percentage
			}));
		}
	}

	NRS.checkIfOnAFork = function() {
		if (!NRS.downloadingBlockchain) {
			var onAFork = true;

			if (NRS.blocks && NRS.blocks.length >= 10) {
				for (var i = 0; i < 10; i++) {
					if (NRS.blocks[i].generator != NRS.account) {
						onAFork = false;
						break;
					}
				}
			}

			if (onAFork) {
				$.growl($.t("fork_warning"), {
					"type": "danger"
				});
			}
		}
	}

	$("#id_search").on("submit", function(e) {
		e.preventDefault();

		var id = $.trim($("#id_search input[name=q]").val());

		if (/NFD\-/i.test(id)) {
			NRS.sendRequest("getAccount", {
				"account": id
			}, function(response, input) {
				if (!response.errorCode) {
					response.account = input.account;
					NRS.showAccountModal(response);
				} else {
					$.growl($.t("error_search_no_results"), {
						"type": "danger"
					});
				}
			});
		} else {
			if (!/^\d+$/.test(id)) {
				$.growl($.t("error_search_invalid"), {
					"type": "danger"
				});
				return;
			}
			NRS.sendRequest("getTransaction", {
				"transaction": id
			}, function(response, input) {
				if (!response.errorCode) {
					response.transaction = input.transaction;
					NRS.showTransactionModal(response);
				} else {
					NRS.sendRequest("getAccount", {
						"account": id
					}, function(response, input) {
						if (!response.errorCode) {
							response.account = input.account;
							NRS.showAccountModal(response);
						} else {
							NRS.sendRequest("getBlock", {
								"block": id
							}, function(response, input) {
								if (!response.errorCode) {
									response.block = input.block;
									NRS.showBlockModal(response);
								} else {
									$.growl($.t("error_search_no_results"), {
										"type": "danger"
									});
								}
							});
						}
					});
				}
			});
		}
	});

	return NRS;
}(NRS || {}, jQuery));

$(document).ready(function() {
	NRS.init();
});

function receiveMessage(event) {
	if (event.origin != "file://") {
		return;
	}
	//parent.postMessage("from iframe", "file://");
}

function _checkDOMenabled() {
	var storage;
	var fail;
	var uid;
	try {
	  uid = new Date;
	  (storage = window.localStorage).setItem(uid, uid);
	  fail = storage.getItem(uid) != uid;
	  storage.removeItem(uid);
	  fail && (storage = false);
	} catch (exception) {}
	return storage;
}