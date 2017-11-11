/**
 * NFDBE - NFD Block Explorer
 * Author: lordoliver
 * Javascript Functionalities for ajax calls on the NFD backend 
 * to search for transactions, blocks and accounts
 *
**/

$(function(){
	var NFDBE = {};
	var id = getUrlParameter('id');
	
	function getUrlParameter(sParam)
	{
	    var sPageURL = window.location.search.substring(1);
	    var sURLVariables = sPageURL.split('&');
	    for (var i = 0; i < sURLVariables.length; i++) 
	    {
	        var sParameterName = sURLVariables[i].split('=');
	        if (sParameterName[0] == sParam) 
	        {
	            return sParameterName[1];
	        }
	    }
	}
	
	/* triggers the click event for all functional links */
//	$("#searchResult").on('click',"a[class^='function_']",function(){
//		var clses = $(this).attr('class').split(' ');
//		for (var i=0; i<clses.length; i++){
//			var clsSplit = clses[i].split("_");
//			if (clsSplit.length == 2 && clsSplit[0]==="function"){
//				NFDBE[clsSplit[1]]($(this).text(),function(result){
//					$( "#searchResult" ).html(result);
//				});
//			}
//		}
//		return false;
//	});

	NFDBE.getAccount = function (id,cb){
		$.get( "/nxt",{
			account:id,
			requestType:'getAccount'
		}, function( data ) {
			if (data.errorCode) cb(null);
			else cb(new EJS({url: 'templates/account'}).render(data));
		}, "json" );
	}

	NFDBE.getTransaction = function (id,cb){
		$.get( "/nxt",{
			transaction:id,
			requestType:'getTransaction'
		}, function( data ) {
			if (data.errorCode) cb(null);
			else cb(new EJS({url: 'templates/transaction'}).render(data));
		}, "json" );
	}
	
	NFDBE.getTransactionData = function (id,cb){
		$.get( "/nxt",{
			transaction:id,
			requestType:'getTransaction'
		}, cb, "json" );
	}
	
	NFDBE.getBlock = function(id,cb){
		$.get( "/nxt",{
			block:id,
			requestType:'getBlock'
		}, function( data ) {
			if (data.errorCode) cb(null);
			else {
				data.block = id;
				cb(new EJS({url: 'templates/block'}).render(data));
			}
		}, "json" );
	}
		
	NFDBE.dataComplete = function(){
		$( "#searchResult .dataCompleteTransaction" ).each(function(){
			var id = $(this).find('td:first a').text();
			var columns = $(this).find('td');
			NFDBE.getTransactionData(id,function(result){
				if (result == null){
					console.log('Transaction does not exist: '+id);
				}
				else{
					$(columns[1]).append($('<a>').text(result.sender).attr('href',"?id="+result.sender));
					$(columns[2]).append($('<a>').text(result.recipient).attr('href',"?id="+result.recipient));
					$(columns[3]).append(result.timestamp);
					$(columns[4]).append("" + result.amountNQT/100000000 + " NFD");
					$(columns[5]).append(result.confirmations);
				} 
			});
		});
	}
	
//	$('#searchForm').bind('submit',function(){
//		var id = $(this).find('input[name=searchId]').val();
//		console.log(id);
//		NFDBE.getTransaction(id,function(result){
//			if (result == null){
//				NFDBE.getBlock(id,function(result){
//					if (result == null){
//						NFDBE.getAccount(id,function(result){
//							if (result == null){
//								$( "#searchResult" ).html("No such Id <b>"+id+"</b>");
//							}
//							else 
//								$( "#searchResult" ).html(result);
//						});
//					}
//					else {
//						$( "#searchResult" ).html(result);
//						NFDBE.dataComplete();
//					}
//				});
//			}
//			else 
//				$( "#searchResult" ).html(result);
//		});
//		return false;
//	})
	
	if (id.length > 0) {
		$('#searchForm [name=id]').val(id);
		NFDBE.getTransaction(id,function(result){
			if (result == null){
				NFDBE.getBlock(id,function(result){
					if (result == null){
						NFDBE.getAccount(id,function(result){
							if (result == null){
								$( "#searchResult" ).html("No such Id <b>"+id+"</b>");
							}
							else 
								$( "#searchResult" ).html(result);
						});
					}
					else {
						$( "#searchResult" ).html(result);
						NFDBE.dataComplete();
					}
				});
			}
			else 
				$( "#searchResult" ).html(result);
		});
	}
});
