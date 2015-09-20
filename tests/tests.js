/// <reference path="../.vscode/typings/jasmine/jasmine.d.ts"/>
exports.defineAutoTests = function() {
	describe("Echo test",function(){
		it("motobite should exist", function(){
			expect(window.motobite).toBeDefined();
		});
		
		it("location should exist", function(){
			expect(window.motobite.location).toBeDefined();
		});
		
		it("echo should exist", function(){
			expect(window.motobite.location.echo).toBeDefined();
		});
		
		it("echo should echo same value",function(){
				var message = "HelloWorld";
				var echo = "";
			runs(function(){
				window.motobite.location.echo(message,function(str){echo=str},function(){});
			});
			// waits for echo to return for 60 seconds
			waitsFor(function(){return echo===message},"Echo not heard",60*1000);
			
			expect(echo).toMatch(message);
		});
		
		it("echo should return serially", function(){
			var messages = ["1","2","3","4","5"];
			var echos = [];
			runs(function(){
				for (var index = 0; index < messages.length; index++) {
					var message = messages[index];
					window.motobite.location.echo(message,function(str){echos.push(str)},function(){});
				}
			});
			waitsFor(function(){ return echos.length == 5},"Echos not heard",60*1000);
			expect(echos).toEqual(messages);
		});
	});
}