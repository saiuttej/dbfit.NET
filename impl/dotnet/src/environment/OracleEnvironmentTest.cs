using System;
using System.Collections.Generic;
using System.Text;

using NUnit.Framework;

namespace dbfit {
	[TestFixture]
	public class OracleEnvironmentTest {
		private OracleEnvironment oe=new OracleEnvironment();
		[Test]
		public void CheckEmptyParams() {
			Assert.AreEqual(0,oe.ExtractParamNames("select * from dual").Length);			
		}
		[Test]
		public void CheckSingleParam() {
			Assert.AreEqual(new string[]{"mydate"}, oe.ExtractParamNames("select * from dual where sysdate<:mydate"));
		}
		[Test, Ignore]
		public void CheckMultipleParams() {
			string[] paramnames=oe.ExtractParamNames("select :myname as zeka from dual where sysdate<:mydate");
			Assert.AreEqual(2,paramnames.Length);
			Assert.Contains("mydate",paramnames);
			Assert.Contains("myname", paramnames);
		}
		[Test,Ignore]
		public void CheckMultipleParamsRecurring() {
			string[] paramnames = oe.ExtractParamNames("select :myname,length(:myname) as l, :myname || :mydate as zeka2 from dual where sysdate<:mydate");
			Assert.AreEqual(2, paramnames.Length);
			Assert.Contains("mydate", paramnames);
			Assert.Contains("myname", paramnames);
		}
		[Test]
		public void CheckUnderscore() {
			Assert.AreEqual(new string[] { "my_date" }, oe.ExtractParamNames("select * from dual where sysdate<:my_date"));
		}
	}
}
